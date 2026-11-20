package sle.editor.project

import kotlinx.serialization.json.*
import sle.editor.project.assets.*
import java.nio.file.Files
import kotlin.test.*

class ProjectDocumentsTest {
    @Test fun keypadEnterCapturesTheSameDistinctKeyUsedByStandalone() {
        fun enter(location: Int) = java.awt.event.KeyEvent(java.awt.Canvas(), java.awt.event.KeyEvent.KEY_PRESSED,
            0, 0, java.awt.event.KeyEvent.VK_ENTER, '\n', location)
        assertEquals(257, sle.editor.viewport.engineKey(enter(java.awt.event.KeyEvent.KEY_LOCATION_STANDARD)))
        assertEquals(335, sle.editor.viewport.engineKey(enter(java.awt.event.KeyEvent.KEY_LOCATION_NUMPAD)))
    }

    private fun project()=ProjectFiles.create(Files.createTempDirectory("sle-doc-").resolve("Game"),"Game")
    @Test fun inputActionsRoundTripAndRejectInvalidBindings() {
        val opened = project()
        fun withActions(entries: String) = ProjectDocument.parse(JsonObject(opened.document.json() +
            ("settings" to Json.parseToJsonElement("""{"inputActions":$entries}"""))).toString())
        val initial = withActions("""[
            {"name":"Jump","bindings":[{"kind":"key","key":32},{"kind":"key","key":87}]},
            {"name":"Confirm","bindings":[{"kind":"key","key":335}]},
            {"name":"Unbound","bindings":[]}
        ]""")
        ProjectFiles.save(opened, initial)
        assertEquals(initial, ProjectFiles.open(opened.file).document)
        for (bad in listOf(
            """[{"name":"Jump","bindings":[]},{"name":"Jump","bindings":[]}]""",
            """[{"name":"","bindings":[]}]""",
            """[{"name":"Jump","bindings":[{"kind":"mouse","key":32}]}]""",
            """[{"name":"Jump","bindings":[{"kind":"key","key":2147483648}]}]""",
            """[{"name":"Jump","bindings":[{"kind":"key","key":"32"}]}]""",
            """[{"name":"Jump","bindings":[{"kind":"key","key":32},{"kind":"key","key":32}]}]""",
        )) assertFails { withActions(bad) }
    }

    @Test fun tagDefinitionsKeepTheirIdsOnRenameAndReopen() {
        val opened = project()
        fun withTags(entries: String) = ProjectDocument.parse(JsonObject(opened.document.json() +
            ("settings" to Json.parseToJsonElement("""{"tags":$entries}"""))).toString())
        val initial = withTags("""[{"id":"hero","name":"Player"}]""")
        ProjectFiles.save(opened, initial)
        val saved = ProjectFiles.open(opened.file)
        assertEquals(initial, saved.document)
        val renamed = withTags("""[{"id":"hero","name":"Hero"}]""")
        ProjectFiles.save(saved, renamed)
        assertEquals(renamed, ProjectFiles.open(opened.file).document)
        assertFails { withTags("""[{"id":"a","name":"Hero"},{"id":"b","name":"Hero"}]""") }
        assertFails { withTags("""[{"id":"a","name":"Hero"},{"id":"a","name":"Player"}]""") }
    }

    @Test fun artStyleIsOptionalStrictAndRoundTrips() {
        val legacy = project()
        assertNull(legacy.document.settings.artStyle)
        for (style in ProjectArtStyle.entries) {
            val created = ProjectFiles.create(legacy.root.resolveSibling(style.token), "Game", style)
            assertEquals(style, ProjectFiles.open(created.file).document.settings.artStyle)
        }
        val candidate = legacy.document.copy(settings = ProjectSettings(ProjectArtStyle.PixelArt))
        assertNull(ProjectFiles.open(legacy.file).document.settings.artStyle)
        ProjectFiles.save(legacy, candidate)
        assertEquals(candidate, ProjectFiles.open(legacy.file).document)
        for (bad in listOf("null", "true", "{}", "\"unknown\"")) {
            val invalid = legacy.document.json() + ("settings" to Json.parseToJsonElement("""{"artStyle":$bad}"""))
            assertFails { ProjectDocument.parse(JsonObject(invalid).toString()) }
        }
    }

    @Test fun collisionLayersRoundTripAndPreserveBitSlots() {
        val project = project()
        val defaults = project.document.settings.collisionLayers
        assertEquals("16", defaults.label(15))
        assertTrue(defaults.allows(0, 15))

        val layers = defaults.withLabel(15, "Terrain").withPair(15, 0, false)
        assertFalse(layers.allows(0, 15))
        assertFalse(layers.allows(15, 0))
        assertTrue(layers.allows(1, 15))
        val candidate = project.document.copy(settings = ProjectSettings(collisionLayers = layers))
        ProjectFiles.save(project, candidate)
        val reopened = ProjectFiles.open(project.file).document
        assertEquals(candidate, reopened)
        assertEquals("Terrain", reopened.settings.collisionLayers.label(15))
        assertEquals("16", layers.withLabel(15, "").label(15))

        for (bad in listOf("""{"labels":[1]}""", """{"blockedPairs":[[0,16]]}""",
            """{"blockedPairs":[["0",1]]}""", """{"blockedPairs":[[0,1,2]]}""")) {
            val invalid = candidate.json() + ("settings" to Json.parseToJsonElement("""{"collisionLayers":$bad}"""))
            assertFails { ProjectDocument.parse(JsonObject(invalid).toString()) }
        }
    }

    @Test fun sortingLayersRoundTripBySignedValueAndRejectDuplicates() {
        val project = project()
        val source = project.document.json()
        fun parse(entries: String) = ProjectDocument.parse(JsonObject(source +
            ("settings" to Json.parseToJsonElement("""{"sortingLayers":$entries}"""))).toString())

        val labeled = parse("""[{"value":7,"label":"Foreground"},{"value":-2,"label":"Behind"}]""")
        ProjectFiles.save(project, labeled)
        val reopened = ProjectFiles.open(project.file)
        assertEquals(labeled, reopened.document)
        val edited = labeled.copy(settings = labeled.settings.copy(sortingLayers = mapOf(-2 to "Backdrop")))
        ProjectFiles.save(reopened, edited)
        assertEquals(edited, ProjectFiles.open(project.file).document)
        assertFails { parse("""[{"value":7,"label":"One"},{"value":7,"label":"Two"}]""") }
        assertFails { parse("""[{"value":2147483648,"label":"Too large"}]""") }
        assertFails { parse("""[{"value":"7","label":"String"}]""") }
    }

    @Test fun behaviorParameterStringsSurviveProjectSaveAndReopen() {
        val opened = project()
        val asset = opened.assets.resolve("Scripts/probe.lua")
        Files.writeString(asset, "return {}")
        val overrides = buildJsonObject {
            put("scriptAsset", "ordinary text")
            put("texture", "project-asset://ordinary-text")
        }
        val source = buildJsonObject {
            put("entities", buildJsonArray {
                add(buildJsonObject {
                    put("key", "actor")
                    put("components", buildJsonObject {
                        put("Script", buildJsonObject {
                            put("behaviours", buildJsonArray {
                                add(buildJsonObject {
                                    put("key", "probe")
                                    put("scriptAsset", asset.toString().replace('\\', '/'))
                                    put("parameters", overrides)
                                })
                            })
                        })
                    })
                })
            })
        }
        val documents = ProjectDocuments(opened)
        val path = opened.assets.resolve("Scenes/parameters.scene.json")
        documents.save(path, ProjectDocumentKind.Scene, source.toString(), emptyMap(), null) {}
        val reopened = Json.parseToJsonElement(documents.open(path).source).jsonObject
        val attachment = reopened.getValue("entities").jsonArray.single().jsonObject
            .getValue("components").jsonObject.getValue("Script").jsonObject
            .getValue("behaviours").jsonArray.single().jsonObject
        assertEquals(overrides, attachment.getValue("parameters"))
        assertEquals(asset, java.nio.file.Path.of(attachment.getValue("scriptAsset").jsonPrimitive.content))
    }

    private fun source(texture: String)=buildJsonObject {
        put("entities",buildJsonArray { add(buildJsonObject {
            put("key","actor");put("name",texture)
            put("components",buildJsonObject {
                put("SpriteRenderer",buildJsonObject { put("texture",texture) })
                put("Script",buildJsonObject { put("variables",buildJsonObject { put("texture",texture) }) })
            })
        }) })
    }.toString()
    @Test fun sharesAssetsAndResolvesAfterRelocationWithoutRewritingNamesOrVariables() {
        val p=project();val texture=p.assets.resolve("Textures/shared.png");Files.write(texture,byteArrayOf(1,2,3))
        val store=ProjectDocuments(p);val text=source(texture.toString().replace('\\','/'))
        val first=store.save(p.assets.resolve("Scenes/a.scene.json"),ProjectDocumentKind.Scene,text,emptyMap(),null) {}
        store.save(p.assets.resolve("Scenes/b.scene.json"),ProjectDocumentKind.Scene,text,emptyMap(),null) {}
        val raw=Files.readString(first.path)
        assertTrue(raw.contains("project-asset://"))
        assertEquals(2,Files.list(p.assets.resolve("Textures")).use { it.count() }.toInt()) // payload plus metadata
        val moved=p.root.resolveSibling("Relocated");Files.move(p.root,moved)
        val reopened=ProjectFiles.open(moved.resolve(p.file.fileName))
        val loaded=ProjectDocuments(reopened).open(reopened.assets.resolve("Scenes/a.scene.json"))
        val entity=Json.parseToJsonElement(loaded.source).jsonObject.getValue("entities").jsonArray.single().jsonObject
        assertEquals(reopened.assets.resolve("Textures/shared.png").toString().replace('\\','/'),entity.getValue("components").jsonObject.getValue("SpriteRenderer").jsonObject.getValue("texture").jsonPrimitive.content)
        assertEquals(texture.toString().replace('\\','/'),entity.getValue("name").jsonPrimitive.content)
        assertEquals(texture.toString().replace('\\','/'),entity.getValue("components").jsonObject.getValue("Script").jsonObject.getValue("variables").jsonObject.getValue("texture").jsonPrimitive.content)
    }
    @Test fun propertyResolutionUsesExistingIdentityWithoutRegisteringAssets() {
        val project = project()
        val store = ProjectDocuments(project)
        val texture = Files.writeString(project.assets.resolve("Textures/registered.png"), "texture")
        val id = AssetIdentity.ensure(texture)
        val unregistered = Files.writeString(project.assets.resolve("Textures/unregistered.png"), "texture")
        val foreign = Files.writeString(project().assets.resolve("Textures/foreign.png"), "texture")
        val foreignId = AssetIdentity.ensure(foreign)
        val expected = texture.toRealPath().toString().replace('\\', '/')
        assertEquals(expected, store.resolvePropertyAsset(texture.toString()))
        assertEquals(expected, store.resolvePropertyAsset("project-asset://$id"))
        assertFails { store.resolvePropertyAsset(unregistered.toString()) }
        assertFails { store.resolvePropertyAsset(foreign.toString()) }
        assertFails { store.resolvePropertyAsset("project-asset://$foreignId") }
        assertFalse(Files.exists(AssetIdentity.sidecar(unregistered)))
    }

    @Test fun externalChangesAndFailedValidationPreserveDisk() {
        val p=project();val store=ProjectDocuments(p);val path=p.assets.resolve("Prefabs/a.prefab.json")
        val opened=store.save(path,ProjectDocumentKind.Prefab,"{\"root\":\"a\",\"entities\":[]}",emptyMap(),null) {}
        val before=Files.readString(path)
        assertFails { store.save(path,opened.kind,"{}",emptyMap(),opened) { error("invalid") } }
        assertEquals(before,Files.readString(path))
        Files.writeString(path,before+" ")
        assertFails { store.save(path,opened.kind,"{}",emptyMap(),opened) {} }
        assertEquals(before+" ",Files.readString(path))
        Files.delete(path)
        assertFails { store.save(path,opened.kind,"{}",emptyMap(),opened) {} }
        assertFalse(Files.exists(path))
    }
    @Test fun missingDuplicateAndOutsideAssetsCannotBeSilentlyBound() {
        val p=project();val store=ProjectDocuments(p);val asset=p.assets.resolve("Textures/a.png");Files.writeString(asset,"image")
        val saved=store.save(p.assets.resolve("Scenes/a.scene.json"),ProjectDocumentKind.Scene,source(asset.toString()),emptyMap(),null) {}
        val duplicate=p.assets.resolve("Textures/b.png");Files.copy(asset,duplicate);Files.copy(AssetIdentity.sidecar(asset),AssetIdentity.sidecar(duplicate))
        assertFails { store.open(saved.path) }
        Files.delete(duplicate);Files.delete(AssetIdentity.sidecar(duplicate));Files.delete(asset)
        assertFails { store.open(saved.path) }
        val external=Files.createTempFile("outside",".png")
        assertFails { store.save(saved.path,ProjectDocumentKind.Scene,source(external.toString()),emptyMap(),saved) {} }
        assertFails { store.save(p.root.resolve("outside.scene.json"),ProjectDocumentKind.Scene,"{}",emptyMap(),null) {} }
    }
    @Test fun invalidDestinationMetadataCannotReplacePayload() {
        val p=project();val store=ProjectDocuments(p);val path=p.assets.resolve("Scenes/a.scene.json")
        val saved=store.save(path,ProjectDocumentKind.Scene,"{}",emptyMap(),null) {}
        Files.writeString(AssetIdentity.sidecar(path),"broken metadata")
        val before=Files.readString(path)
        assertFails { store.save(path,ProjectDocumentKind.Scene,"{\"entities\":[]}",emptyMap(),saved) {} }
        assertEquals(before,Files.readString(path))
    }
    @Test fun fileArrivingDuringValidationIsPreserved() {
        val p=project();val store=ProjectDocuments(p);val path=p.assets.resolve("Scenes/a.scene.json")
        assertFails { store.save(path,ProjectDocumentKind.Scene,"{}",emptyMap(),null) { Files.writeString(path,"external arrival") } }
        assertEquals("external arrival",Files.readString(path))
    }

}
