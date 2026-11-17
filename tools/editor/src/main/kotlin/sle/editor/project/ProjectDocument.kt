package sle.editor.project

import kotlinx.serialization.json.*
import java.util.UUID

internal data class ProjectGameSettings(
    val title: String="New Game", val width: Int=1280, val height: Int=720,
    val vsync: Boolean=true, val mode: String="Windowed", val targetFPS: Int=0,
)

internal data class ProjectDocument(
    val id: String=UUID.randomUUID().toString(), val name: String="New Game",
    val assetRoot: String="Assets", val startupScene: String?=null,
    val game: ProjectGameSettings=ProjectGameSettings(title=name),
) {
    fun validate() {
        require(validId(id)) { "Invalid project UUID" }
        require(name.isNotBlank() && name.length<=256 && name.none(Char::isISOControl)) { "Enter a project name (1–256 characters)" }
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
            checkKeys(root,setOf("format","version","id","name","assetRoot","startupScene","game"))
            require(text(root,"format")=="sle-project") { "Not an SLE project" }
            require(number(root,"version")==1) { "Unsupported project version" }
            val game=root["game"]?.jsonObject ?: error("Missing game settings")
            checkKeys(game,setOf("window","targetFPS"))
            val window=game["window"]?.jsonObject ?: error("Missing game window settings")
            checkKeys(window,setOf("title","width","height","vsync","mode"))
            val vsync=window["vsync"]?.jsonPrimitive
            require(vsync!=null && !vsync.isString && vsync.booleanOrNull!=null) { "Invalid vsync" }
            val startup=root["startupScene"]
            return ProjectDocument(text(root,"id"),text(root,"name"),text(root,"assetRoot"),
                if(startup==null || startup==JsonNull) null else text(root,"startupScene"),
                ProjectGameSettings(text(window,"title"),number(window,"width"),number(window,"height"),vsync.boolean,text(window,"mode"),number(game,"targetFPS"))).also { it.validate() }
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
