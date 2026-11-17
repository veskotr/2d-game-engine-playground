package sle.editor.project.assets

import java.nio.file.*
import kotlin.test.*

class ProjectAssetImportsTest {
    private val project=Files.createTempDirectory("sle-import-")
    private val assets=Files.createDirectory(project.resolve("Assets"))
    private val imports=ProjectAssetImports(project,assets)
    @Test fun copiesFoldersPreservesModulesAndReusesProjectSources() {
        val source=Files.createTempDirectory("sle-source-")
        Files.createDirectories(source.resolve("modules"))
        Files.writeString(source.resolve("main.lua"),"require('modules.util')")
        Files.writeString(source.resolve("modules/util.lua"),"return {}")
        val result=imports.importAsset(source,"")
        val target=assertNotNull(result.path)
        Files.delete(source.resolve("modules/util.lua"));Files.delete(source.resolve("main.lua"))
        assertEquals("return {}",Files.readString(target.resolve("modules/util.lua")))
        val snapshot=AssetScanner.scan(project,assets)
        assertEquals(2,snapshot.pathsById.size)
        assertTrue(imports.importAsset(target,"",ImportCollision.Rename).reused)
        assertEquals(2,AssetScanner.scan(project,assets).pathsById.size)
    }
    @Test fun collisionsRequireChoiceAndReplacementPreservesDestinationIdentity() {
        val source=Files.writeString(Files.createTempDirectory("sle-source-").resolve("a.lua"),"first")
        val target=assertNotNull(imports.importAsset(source,"").path)
        val id=AssetScanner.scan(project,assets).idsByPath.getValue("a.lua")
        Files.writeString(source,"second")
        assertFailsWith<IllegalArgumentException> { imports.importAsset(source,"") }
        assertNull(imports.importAsset(source,"",ImportCollision.Skip).path)
        val renamed=assertNotNull(imports.importAsset(source,"",ImportCollision.Rename).path)
        assertNotEquals(target,renamed)
        imports.importAsset(source,"",ImportCollision.Replace)
        assertEquals("second",Files.readString(target))
        assertEquals(id,AssetScanner.scan(project,assets).idsByPath["a.lua"])
        assertEquals("second",Files.readString(source))
    }
    @Test fun duplicateImportedIdentityAndEscapesAreRejectedBeforeCopy() {
        val source=Files.writeString(Files.createTempDirectory("sle-source-").resolve("a.lua"),"a")
        val target=assertNotNull(imports.importAsset(source,"").path)
        Files.writeString(AssetIdentity.sidecar(source),Files.readString(AssetIdentity.sidecar(target)))
        assertFails { imports.importAsset(source,"",ImportCollision.Rename) }
        assertEquals(1,AssetScanner.scan(project,assets).pathsById.size)
        assertFails { imports.importAsset(source,"../outside") }
    }
    @Test fun invalidNestedMetadataDoesNotLeavePartialFolderCopy() {
        val source=Files.createTempDirectory("sle-source-")
        Files.writeString(source.resolve("a.lua"),"a")
        Files.writeString(source.resolve("z.lua"),"z")
        Files.writeString(AssetIdentity.sidecar(source.resolve("z.lua")),"invalid")
        assertFails { imports.importAsset(source,"") }
        assertFalse(Files.exists(assets.resolve(source.fileName)))
    }
    @Test fun linkSourceAndLinkedDestinationAreRejected() {
        val outside=Files.createTempDirectory("sle-outside-")
        val source=Files.writeString(outside.resolve("a.lua"),"a")
        try { Files.createSymbolicLink(assets.resolve("link"),outside) }
        catch(failure: Exception) { org.junit.Assume.assumeNoException("Host cannot create symbolic links",failure) }
        assertFails { imports.importAsset(source,"link") }
        val linked=Files.createSymbolicLink(outside.resolve("source-link.lua"),source)
        assertFails { imports.importAsset(linked,"") }
    }
}
