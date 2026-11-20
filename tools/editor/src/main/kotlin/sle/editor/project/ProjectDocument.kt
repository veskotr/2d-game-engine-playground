package sle.editor.project

import kotlinx.serialization.json.*
import java.util.UUID
import sle.editor.viewport.engineKeyName

internal data class ProjectGameSettings(
    val title: String="New Game", val width: Int=1280, val height: Int=720,
    val vsync: Boolean=true, val mode: String="Windowed", val targetFPS: Int=0,
)

internal enum class ProjectArtStyle(val token: String, val label: String) {
    PixelArt("pixelArt", "Pixel Art"),
    Smooth("smooth", "Smooth"),
}

internal data class KeyboardBinding(val key: Int)

internal data class ProjectSettings(
    val artStyle: ProjectArtStyle? = null,
    val collisionLayers: CollisionLayers = CollisionLayers(),
    val sortingLayers: Map<Int, String> = emptyMap(),
    val tags: Map<String, String> = emptyMap(),
    val inputActions: Map<String, List<KeyboardBinding>> = emptyMap(),
) {
    fun boundKeys() = inputActions.values.flatten().mapTo(mutableSetOf()) { it.key }

    fun json() = buildJsonObject {
        if (inputActions.isNotEmpty()) put("inputActions", buildJsonArray {
            inputActions.toSortedMap().forEach { (name, bindings) ->
                add(buildJsonObject {
                    put("name", name)
                    put("bindings", buildJsonArray {
                        bindings.forEach { binding ->
                            add(buildJsonObject { put("kind", "key"); put("key", binding.key) })
                        }
                    })
                })
            }
        })
        artStyle?.let { put("artStyle", it.token) }
        val layers = collisionLayers.json()
        if (layers.isNotEmpty()) put("collisionLayers", layers)
        if (tags.isNotEmpty()) put("tags", buildJsonArray {
            tags.toSortedMap().forEach { (id, name) ->
                add(buildJsonObject { put("id", id); put("name", name) })
            }
        })
        if (sortingLayers.isNotEmpty()) put("sortingLayers", buildJsonArray {
            sortingLayers.toSortedMap().forEach { (value, label) ->
                add(buildJsonObject { put("value", value); put("label", label) })
            }
        })
    }
}

internal data class ProjectDocument(
    val id: String=UUID.randomUUID().toString(), val name: String="New Game",
    val assetRoot: String="Assets", val startupScene: String?=null,
    val game: ProjectGameSettings=ProjectGameSettings(title=name),
    val settings: ProjectSettings=ProjectSettings(),
) {
    fun validate() {
        require(validId(id)) { "Invalid project UUID" }
        require(name.isNotBlank() && name.length<=256 && name.none(Char::isISOControl)) { "Enter a project name (1–256 characters)" }
        require(settings.sortingLayers.values.none(String::isEmpty)) { "Enter a sorting layer label" }
        require(settings.tags.keys.none(String::isEmpty) && settings.tags.values.none(String::isEmpty) &&
            settings.tags.values.toSet().size == settings.tags.size) { "Enter unique nonempty tag IDs and names" }
        require(settings.inputActions.keys.none(String::isEmpty)) { "Enter a nonempty input action name" }
        settings.inputActions.values.forEach { bindings ->
            require(bindings.all { engineKeyName(it.key) != null }) { "Unsupported input action key" }
            require(bindings.toSet().size == bindings.size) { "Duplicate input action key" }
        }
        relativeProjectPath(assetRoot)
        require(!assetRoot.substringBefore('/').equals(".sle",true)) { "Asset root cannot use local editor storage" }
        require(startupScene==null || validId(startupScene)) { "Invalid startup scene UUID" }
        require(game.title.isNotBlank() && game.title.length<=512 && game.title.none(Char::isISOControl)) { "Enter a game window title" }
        require(game.width in 1..16384 && game.height in 1..16384) { "Window dimensions must be between 1 and 16384" }
        require(game.mode in listOf("Windowed","Fullscreen","BorderlessFullscreen")) { "Invalid window mode" }
        require(game.targetFPS>=0) { "Target FPS must be nonnegative (0 is unlimited)" }
    }
    fun json(): JsonObject {
        validate()
        return buildJsonObject {
            put("format","sle-project");put("version",1);put("id",id);put("name",name);put("assetRoot",assetRoot)
            put("startupScene",startupScene?.let(::JsonPrimitive) ?: JsonNull)
            settings.json().takeIf { it.isNotEmpty() }?.let { put("settings", it) }
            put("game",buildJsonObject {
                put("window",buildJsonObject {
                    put("title",game.title);put("width",game.width);put("height",game.height)
                    put("vsync",game.vsync);put("mode",game.mode)
                });put("targetFPS",game.targetFPS)
            })
        }
    }
    fun serialize()=Json { prettyPrint=true }.encodeToString(JsonObject.serializer(),json())
    companion object {
        fun parse(source: String): ProjectDocument {
            val root=Json.parseToJsonElement(source).jsonObject
            checkKeys(root,setOf("format","version","id","name","assetRoot","startupScene","game","settings"))
            require(text(root,"format")=="sle-project") { "Not an SLE project" }
            require(number(root,"version")==1) { "Unsupported project version" }
            val game=root["game"]?.jsonObject ?: error("Missing game settings")
            checkKeys(game,setOf("window","targetFPS"))
            val window=game["window"]?.jsonObject ?: error("Missing game window settings")
            checkKeys(window,setOf("title","width","height","vsync","mode"))
            val vsync=window["vsync"]?.jsonPrimitive
            require(vsync!=null && !vsync.isString && vsync.booleanOrNull!=null) { "Invalid vsync" }
            val settings = root["settings"]?.jsonObject?.let {
                checkKeys(it, setOf("artStyle", "collisionLayers", "sortingLayers", "tags", "inputActions"))
                ProjectSettings(if ("artStyle" in it) {
                    val token = text(it, "artStyle")
                    requireNotNull(ProjectArtStyle.entries.find { style -> style.token == token }) { "Invalid project art style" }
                } else null, it["collisionLayers"]?.let(CollisionLayers::parse) ?: CollisionLayers(),
                    parseSortingLayers(it["sortingLayers"]), parseTags(it["tags"]), parseInputActions(it["inputActions"]))
            } ?: ProjectSettings()
            val startup=root["startupScene"]
            return ProjectDocument(text(root,"id"),text(root,"name"),text(root,"assetRoot"),
                if(startup==null || startup==JsonNull) null else text(root,"startupScene"),
                ProjectGameSettings(text(window,"title"),number(window,"width"),number(window,"height"),vsync.boolean,text(window,"mode"),number(game,"targetFPS")), settings).also { it.validate() }
        }
        private fun parseInputActions(element: JsonElement?): Map<String, List<KeyboardBinding>> = buildMap {
            for (item in element?.jsonArray.orEmpty()) {
                val entry = item.jsonObject
                checkKeys(entry, setOf("name", "bindings"))
                val name = text(entry, "name")
                require(name !in this) { "Duplicate input action: $name" }
                put(name, entry.getValue("bindings").jsonArray.map {
                    val binding = it.jsonObject
                    checkKeys(binding, setOf("kind", "key"))
                    require(text(binding, "kind") == "key") { "Expected a keyboard binding" }
                    KeyboardBinding(number(binding, "key"))
                })
            }
        }

        private fun parseTags(element: JsonElement?): Map<String, String> = buildMap {
            for (item in element?.jsonArray.orEmpty()) {
                val entry = item.jsonObject
                checkKeys(entry, setOf("id", "name"))
                val id = text(entry, "id")
                require(id !in this) { "Duplicate tag ID: $id" }
                put(id, text(entry, "name"))
            }
        }

        private fun parseSortingLayers(element: JsonElement?): Map<Int, String> = buildMap {
            for (item in element?.jsonArray.orEmpty()) {
                val entry = item.jsonObject
                checkKeys(entry, setOf("value", "label"))
                val value = number(entry, "value")
                require(value !in this) { "Duplicate sorting layer value: $value" }
                put(value, text(entry, "label"))
            }
        }
        private fun checkKeys(value: JsonObject,allowed: Set<String>) {
            require(value.keys.all { it in allowed }) { "Unknown project field: ${value.keys-allowed}" }
        }
        private fun text(value: JsonObject,key: String): String {
            val result=value[key] as? JsonPrimitive
            require(result!=null && result.isString) { "Expected text for $key" };return result.content
        }
        private fun number(value: JsonObject,key: String): Int {
            val result=value[key] as? JsonPrimitive
            require(result!=null && !result.isString && result.intOrNull!=null) { "Expected integer for $key" };return result.int
        }
    }
}

private fun validId(value: String)=runCatching { UUID.fromString(value).toString().equals(value,true) }.getOrDefault(false)

internal fun relativeProjectPath(value: String): List<String> {
    require(value.isNotBlank() && !value.startsWith('/') && '\\' !in value && ':' !in value) { "Expected a project-relative path" }
    val parts=value.split('/')
    require(parts.none { it.isEmpty() || it=="." || it==".." || it.any(Char::isISOControl) }) { "Project path cannot escape its root" }
    return parts
}

internal fun validateProjectFolderName(name: String) {
    require(name.isNotBlank() && name==name.trim() && name !in listOf(".","..") && name.none { it in "<>:\"/\\|?*" || it.isISOControl() } && !name.endsWith('.')) { "Enter a valid project folder name" }
    val base=name.substringBefore('.').uppercase()
    require(base !in setOf("CON","PRN","AUX","NUL") && !base.matches(Regex("(COM|LPT)[1-9]"))) { "Reserved project folder name" }
}
