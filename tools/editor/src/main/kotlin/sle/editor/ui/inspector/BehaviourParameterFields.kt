package sle.editor.ui.inspector

import androidx.compose.material.MaterialTheme
import sle.editor.ui.controls.EditorLabel
import androidx.compose.runtime.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import sle.editor.bridge.NativeEngine
import sle.editor.model.*
import sle.editor.project.assets.LocalProjectAssets
import sle.editor.project.assets.assetPath
import sle.editor.scripts.LocalOpenScriptAttachment
import sle.editor.ui.controls.*
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes

private const val sourceLimit = 256 * 1024

private data class ScriptRevision(val path: Path, val attributes: BasicFileAttributes) {
    fun matches(other: ScriptRevision): Boolean =
        path == other.path && attributes.lastModifiedTime() == other.attributes.lastModifiedTime() &&
            attributes.size() == other.attributes.size() && attributes.fileKey() == other.attributes.fileKey()
}

private data class Discovery(val catalog: ComponentDefinition? = null, val error: String? = null)

@Composable
internal fun BehaviourParameterFields(state: EntityWorkspaceState, script: JsonObject, readOnly: Boolean) {
    val project = LocalProjectAssets.current
    val root = project?.projectRoot
    val assets = project?.assetRoot
    val openScript = LocalOpenScriptAttachment.current
    if (openScript != null) script.behaviourText("scriptAsset")?.let { asset ->
        ScriptSourceRow("Primary script") { openScript(asset) }
    }
    val entries = script.behaviourEntries()
    val byAsset = entries.mapNotNull { it.behaviourText("scriptAsset") }.distinct().associateWith { asset ->
        val relative = project?.snapshot?.pathsById?.get(asset.removePrefix("project-asset://"))
        key(root, assets, asset, relative) {
            val discovery by produceState(Discovery()) {
                var evaluated: ScriptRevision? = null
                while (true) {
                    try {
                        val version = withContext(Dispatchers.IO) {
                            val path = if (root != null && assets != null) {
                                val authored = if (asset.startsWith("project-asset://")) {
                                    assets.resolve(requireNotNull(relative) { "Script asset is missing" })
                                } else Path.of(asset).let { if (it.isAbsolute) it else assets.resolve(it) }
                                val assetRoot = assets.toAbsolutePath().normalize()
                                val absolute = authored.toAbsolutePath().normalize()
                                require(absolute.startsWith(assetRoot)) { "Script is outside the project assets" }
                                assetPath(root, assets, assetRoot.relativize(absolute).toString().replace('\\', '/'))
                            } else {
                                Path.of(NativeEngine.resolveStandaloneAsset(asset.encodeToByteArray()).decodeToString())
                            }
                            ScriptRevision(path, Files.readAttributes(path, BasicFileAttributes::class.java))
                        }
                        if (evaluated?.matches(version) != true) {
                            value = withContext(Dispatchers.IO) { discover(version) }
                            evaluated = version
                        }
                    } catch (failure: CancellationException) {
                        throw failure
                    } catch (failure: Exception) {
                        value = Discovery(error = failure.message ?: "Could not read behavior properties")
                        evaluated = null
                    } catch (_: UnsatisfiedLinkError) {
                        value = Discovery(error = "The editor bridge needs to be rebuilt to read behavior properties")
                        break
                    }
                    delay(1000)
                }
            }
            discovery
        }
    }
    entries.forEach { entry ->
        val attachment = entry.behaviourText("key") ?: return@forEach
        val asset = entry.behaviourText("scriptAsset") ?: return@forEach
        val discovery = byAsset[asset] ?: return@forEach
        val catalog = discovery.catalog
        val hasProperties = catalog?.fields?.isNotEmpty() == true || discovery.error != null
        if (!hasProperties && openScript == null) return@forEach
        key(state, state.document.key, attachment, asset) {
            if (openScript != null) ScriptSourceRow(attachment) { openScript(asset) }
            else EditorLabel(attachment, style = MaterialTheme.typography.subtitle2)
            discovery.error?.let { EditorLabel(it, color = MaterialTheme.colors.error, style = MaterialTheme.typography.caption) }
            if (catalog != null) key(discovery) {
                val overrides = (entry["parameters"] as? JsonObject).orEmpty()
                catalog.fields.keys.forEach { path ->
                    val name = path.single()
                    val default = catalog.defaults.getValue(name)
                    val saved = overrides[name]
                    val displayed = if (compatibleParameter(saved, default)) saved!! else default
                    val property = propertyDefinition("Script", path, displayed, catalog = catalog)
                    key(name) {
                        BehaviourParameterField(state, attachment, asset, property, saved != null, readOnly)
                    }
                }
            }
        }
    }
}

@Composable
private fun ScriptSourceRow(label: String, open: () -> Unit) {
    EditorPropertyRow(label, help = "Open the Lua source in your configured IDE") {
        EditorButton("Open in IDE", onClick = open, style = EditorButtonStyle.Ghost)
    }
}

private fun discover(version: ScriptRevision): Discovery {
    require(version.attributes.isRegularFile && version.attributes.size() <= sourceLimit) {
        "Script must be a regular file no larger than 256 KiB"
    }
    val bytes = Files.newInputStream(version.path).use { it.readNBytes(sourceLimit + 1) }
    require(bytes.size <= sourceLimit) { "Script exceeds 256 KiB" }
    val response = NativeEngine.behaviourSchema(bytes, version.path.toString().encodeToByteArray()).decodeToString()
    val result = Json.parseToJsonElement(response).jsonObject
    val error = result.getValue("error").jsonPrimitive.content
    if (error.isNotEmpty()) return Discovery(error = error)
    val catalog = decodeComponentCatalog(buildJsonObject {
        put("Behaviour", JsonObject(result + ("enums" to JsonObject(emptyMap()))))
    }.toString()).getValue("Behaviour")
    return Discovery(catalog)
}

@Composable
private fun BehaviourParameterField(state: EntityWorkspaceState, attachment: String, asset: String,
    property: EditorPropertyDefinition, overridden: Boolean, readOnly: Boolean) {
    val name = property.path.single()
    val entity = state.document.key
    fun write(value: JsonPrimitive?) {
        if (readOnly || state.document.key != entity) return
        patchBehaviourParameter(state.document, attachment, asset, name, value)?.let {
            state.publishPropertyCandidate(it, if (value == null) "Reset ${property.displayLabel}" else "Set ${property.displayLabel}")
        }
    }

    val numberEdit = if (property.kind == PropertyKind.Number) {
        state.behaviourNumberEdit(attachment, asset, name, checkNotNull(property.defaultValue).jsonPrimitive, property.displayLabel)
    } else null
    DisposableEffect(numberEdit, readOnly, property.descriptor) {
        onDispose {
            if (numberEdit?.invalid == true) numberEdit.cancel() else numberEdit?.blur()
        }
    }

    EditorPropertyRenderer(state, property, readOnly, actions = { _, disabled ->
        if (overridden) EditorButton("Reset", { numberEdit?.cancel(); write(null) },
            enabled = !disabled, style = EditorButtonStyle.Ghost)
    }, onScalarEdit = ::write, scalarNumberEdit = numberEdit)
}
