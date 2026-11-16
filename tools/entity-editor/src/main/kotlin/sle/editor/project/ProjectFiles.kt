package sle.editor.project

import kotlinx.serialization.json.*
import java.nio.file.*
import java.nio.file.StandardCopyOption.*

internal data class OpenedProject(val file: Path,val document: ProjectDocument) {
    val root: Path get()=file.parent
    val assets: Path get()=resolve(document.assetRoot)
    fun resolve(relative: String): Path {
        val parts=relativeProjectPath(relative)
        val rootReal=root.toRealPath()
        var target=rootReal
        for(part in parts) {
            target=target.resolve(part)
            if(Files.exists(target,LinkOption.NOFOLLOW_LINKS))
                require(target.toRealPath().startsWith(rootReal)) { "Project path escapes through a link" }
        }
        return target
    }
}

internal data class ProjectEditorState(val uiScale: Float=1f)

internal object ProjectFiles {
    private const val MAX_DESCRIPTOR=1024*1024
    private val folders=listOf("Scenes","Prefabs","Scripts","Textures","Audio","UI","Animations","Data")
    fun create(folder: Path,name: String): OpenedProject {
        val document=ProjectDocument(name=name).also { it.validate() }
        val target=folder.toAbsolutePath().normalize()
        validateProjectFolderName(target.fileName.toString())
        require(Files.isDirectory(target.parent)) { "Choose an existing parent folder" }
        require(!Files.exists(target,LinkOption.NOFOLLOW_LINKS)) { "Project folder already exists; choose a new name" }
        val staging=Files.createTempDirectory(target.parent,".sle-new-project-")
        try {
            for(child in folders) Files.createDirectories(staging.resolve("Assets").resolve(child))
            Files.createDirectories(staging.resolve(".sle/cache"))
            Files.writeString(staging.resolve(".gitignore"),".sle/\n")
            val filename=target.fileName.toString()+".sleproject"
            Files.writeString(staging.resolve(filename),document.serialize())
            // Never replace an existing user directory, including in the non-atomic fallback.
            Files.move(staging,target)
            return open(target.resolve(filename))
        } finally {
            if(Files.exists(staging)) Files.walk(staging).use { stream -> stream.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) } }
        }
    }
    fun open(file: Path): OpenedProject {
        val absolute=file.toAbsolutePath().normalize()
        require(absolute.fileName.toString().endsWith(".sleproject",true)) { "Choose a .sleproject file" }
        require(Files.isRegularFile(absolute) && Files.size(absolute)<=MAX_DESCRIPTOR) { "Missing or oversized project descriptor" }
        val root=absolute.parent.toRealPath()
        val actual=absolute.toRealPath()
        require(actual.startsWith(root)) { "Project descriptor points outside its folder" }
        val source=Files.newInputStream(actual).use { it.readNBytes(MAX_DESCRIPTOR+1) }
        require(source.size<=MAX_DESCRIPTOR) { "Project descriptor exceeds 1 MiB" }
        return OpenedProject(actual,ProjectDocument.parse(source.toString(Charsets.UTF_8))).also {
            require(Files.isDirectory(it.assets)) { "Project asset folder is missing: ${it.document.assetRoot}" }
        }
    }
    fun save(project: OpenedProject,document: ProjectDocument): OpenedProject {
        require(document.id==project.document.id) { "Project identity cannot change" }
        require(open(project.file).document==project.document) { "Project settings changed on disk; reopen the project before saving" }
        val candidate=project.copy(document=document)
        val source=document.serialize()
        require(Files.isDirectory(candidate.assets)) { "Project asset folder is missing" }
        require(project.file.toRealPath().parent==project.root.toRealPath()) { "Project descriptor was moved outside its folder" }
        atomicWrite(project.file,source)
        return candidate
    }
    fun editorState(project: OpenedProject): ProjectEditorState = runCatching {
        val file=project.resolve(".sle/editor-state.json")
        require(Files.size(file)<=16384)
        val source=Json.parseToJsonElement(Files.readString(file)).jsonObject
        val scale=source.getValue("uiScale").jsonPrimitive.float
        require(scale in listOf(0.75f,0.85f,1f))
        ProjectEditorState(scale)
    }.getOrDefault(ProjectEditorState())
    fun saveEditorState(project: OpenedProject,state: ProjectEditorState) {
        require(state.uiScale in listOf(0.75f,0.85f,1f)) { "Unsupported UI scale" }
        val file=project.resolve(".sle/editor-state.json")
        Files.createDirectories(file.parent)
        atomicWrite(file,buildJsonObject { put("uiScale",state.uiScale) }.toString())
    }
    private fun atomicWrite(file: Path,source: String) {
        val temporary=Files.createTempFile(file.parent,".sle-save-",".tmp")
        try {
            Files.writeString(temporary,source)
            try { Files.move(temporary,file,ATOMIC_MOVE,REPLACE_EXISTING) }
            catch(_: AtomicMoveNotSupportedException) { Files.move(temporary,file,REPLACE_EXISTING) }
        } finally { Files.deleteIfExists(temporary) }
    }
}
