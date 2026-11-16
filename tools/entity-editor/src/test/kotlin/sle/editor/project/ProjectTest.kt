package sle.editor.project

import java.nio.file.Files
import java.nio.file.Path
import kotlinx.serialization.json.*
import kotlin.test.*

class ProjectTest {
    private fun temporary()=Files.createTempDirectory("sle-project-test-")
    private fun create(parent: Path=temporary())=ProjectFiles.create(parent.resolve("My Game"),"My Game")

    @Test fun createsOrdinaryProjectFoldersAndRelocatesWithoutAbsolutePaths() {
        val project=create()
        val source=Files.readString(project.file)
        assertEquals("sle-project",Json.parseToJsonElement(source).jsonObject["format"]?.jsonPrimitive?.content)
        assertFalse(source.contains(project.root.toString().replace('\\','/')))
        for(folder in listOf("Scenes","Prefabs","Scripts","Textures","Audio","UI","Animations","Data"))
            assertTrue(Files.isDirectory(project.assets.resolve(folder)))
        assertTrue(Files.readString(project.root.resolve(".gitignore")).contains(".sle/"))
        Files.writeString(project.assets.resolve("Scripts/hello.lua"),"return {}")
        val moved=project.root.resolveSibling("Moved Game")
        Files.move(project.root,moved)
        val reopened=ProjectFiles.open(moved.resolve(project.file.fileName))
        assertEquals(project.document.id,reopened.document.id)
        assertTrue(Files.isRegularFile(reopened.assets.resolve("Scripts/hello.lua")))
    }

    @Test fun descriptorHasSettingsButNoFileInventoryOrEditorState() {
        val project=create()
        val document=project.document.copy(name="Renamed",game=project.document.game.copy(width=960,height=540,vsync=false))
        val saved=ProjectFiles.save(project,document)
        assertEquals(document,ProjectFiles.open(saved.file).document)
        val root=Json.parseToJsonElement(Files.readString(saved.file)).jsonObject
        assertNull(root["files"]);assertNull(root["editorState"])
        assertEquals("Assets",root["assetRoot"]?.jsonPrimitive?.content)
        Files.writeString(saved.assets.resolve("external.txt"),"visible without changing descriptor")
        assertEquals(document,ProjectFiles.open(saved.file).document)
    }

    @Test fun invalidSettingsCannotReplaceFileOrActiveProject() {
        val state=ProjectWorkspaceState();val project=create();state.activate(project)
        val before=Files.readAllBytes(project.file)
        assertFails { state.saveSettings(project.document.copy(game=project.document.game.copy(width=0))) }
        assertContentEquals(before,Files.readAllBytes(project.file))
        assertEquals(project,state.opened)
        assertFails { state.saveSettings(project.document.copy(id=java.util.UUID.randomUUID().toString())) }
    }

    @Test fun malformedAndFutureProjectsDoNotReplaceCurrentWorkspace() {
        val state=ProjectWorkspaceState();val project=create();state.activate(project)
        val invalid=temporary().resolve("bad.sleproject")
        for(source in listOf("{", """{"format":"sle-project","version":99}""", """{"format":"wrong","version":1}""")) {
            Files.writeString(invalid,source)
            assertFails { state.open(invalid) }
            assertEquals(project,state.opened)
        }
        state.close();assertNull(state.opened)
        assertTrue(Files.isRegularFile(project.file))
    }

    @Test fun creationNeverOverwritesExistingDirectoriesAndNamesAreValidated() {
        val parent=temporary();val target=parent.resolve("Existing")
        Files.createDirectories(target);Files.writeString(target.resolve("keep.txt"),"Keep me")
        assertFails { ProjectFiles.create(target,"Game") }
        assertEquals("Keep me",Files.readString(target.resolve("keep.txt")))
        assertFails { ProjectFiles.create(parent.resolve("Invalid"),"  ") }
        assertFalse(Files.exists(parent.resolve("Invalid")))
        for(name in listOf("..","A/B","CON","bad:name")) assertFails { validateProjectFolderName(name) }
    }

    @Test fun rejectsEscapesAndInvalidProjectFields() {
        val project=create()
        for(path in listOf("../Assets","/Assets","C:/Assets",".sle/cache","Assets/../../escape"))
            assertFails { ProjectFiles.save(project,project.document.copy(assetRoot=path)) }
        for(source in listOf(
            project.document.json().let { JsonObject(it+("files" to JsonArray(emptyList()))) }.toString(),
            project.document.json().let { JsonObject(it+("startupScene" to JsonPrimitive("missing-id"))) }.toString(),
        )) assertFails { ProjectDocument.parse(source) }
        assertFails { ProjectFiles.save(project,project.document.copy(game=project.document.game.copy(mode="Other"))) }
        assertFails { ProjectFiles.save(project,project.document.copy(game=project.document.game.copy(targetFPS=-1))) }
    }

    @Test fun settingsSaveRejectsAnExternallyChangedDescriptor() {
        val project=create();val external=project.document.copy(name="Changed in VS Code")
        Files.writeString(project.file,external.serialize())
        assertFails { ProjectFiles.save(project,project.document.copy(name="Stale dialog")) }
        assertEquals(external,ProjectFiles.open(project.file).document)
    }

    @Test fun localStateIsDisposableAndSeparateFromPortableSettings() {
        val project=create();val before=Files.readAllBytes(project.file)
        ProjectFiles.saveEditorState(project,ProjectEditorState(uiScale=0.75f))
        assertEquals(0.75f,ProjectFiles.editorState(project).uiScale)
        assertContentEquals(before,Files.readAllBytes(project.file))
        Files.writeString(project.root.resolve(".sle/editor-state.json"),"broken")
        assertEquals(1f,ProjectFiles.editorState(project).uiScale)
    }
}
