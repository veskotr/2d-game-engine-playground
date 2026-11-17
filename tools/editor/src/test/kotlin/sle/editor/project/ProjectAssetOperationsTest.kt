package sle.editor.project

import sle.editor.project.assets.*
import java.nio.file.Files
import kotlin.test.*

class ProjectAssetOperationsTest {
    private fun project()=ProjectFiles.create(Files.createTempDirectory("sle-ops-").resolve("Game"),"Game")
    @Test fun moveFolderPreservesIdentityAndPortableReferences() {
        val p=project();val file=p.assets.resolve("Textures/a.png");Files.writeString(file,"image")
        val saved=ProjectDocuments(p).save(p.assets.resolve("Scenes/a.scene.json"),ProjectDocumentKind.Scene,"{}",mapOf(file to "a"),null) {}
        val id=AssetIdentity.read(AssetIdentity.sidecar(file));val raw=Files.readString(saved.path)
        val ops=ProjectAssetOperations(p)
        ops.move("Textures","Data/Pictures")
        assertEquals(id,AssetIdentity.read(p.assets.resolve("Data/Pictures/a.png.slemeta")))
        assertEquals(raw,Files.readString(saved.path))
        assertTrue(ProjectDocuments(p).open(saved.path).assets.containsKey(p.assets.resolve("Data/Pictures/a.png")))
    }
    @Test fun deletionReportsReferencesRequiresOverrideAndCanRestore() {
        val p=project();val file=p.assets.resolve("Textures/a.png");Files.writeString(file,"image")
        val scene=ProjectDocuments(p).save(p.assets.resolve("Scenes/a.scene.json"),ProjectDocumentKind.Scene,"{}",mapOf(file to "a"),null) {}
        val ops=ProjectAssetOperations(p)
        assertEquals(listOf("Scenes/a.scene.json"),ops.references("Textures").map { it.source }.distinct())
        assertFails { ops.delete("Textures") }
        assertTrue(Files.exists(file))
        val receipt=ops.delete("Textures",allowReferenced=true)
        assertFalse(Files.exists(file));assertTrue(Files.exists(scene.path))
        ops.restore(receipt)
        assertTrue(Files.exists(file));assertEquals(1,ProjectDocuments(p).open(scene.path).assets.size)
        assertFails { ops.restore(receipt) }
    }
    @Test fun rejectsCollisionsEscapesReservedNamesAndMovingIntoSelf() {
        val p=project();val ops=ProjectAssetOperations(p)
        ops.createFolder("Data/New")
        assertFails { ops.createFolder("../escape") }
        assertFails { ops.createFolder("Data/x.slemeta") }
        assertFails { ops.move("Data","Data/New/Child") }
        assertFails { ops.move("Textures","Data") }
        assertFails { ops.delete("") }
        assertTrue(Files.isDirectory(p.assets.resolve("Textures")))
    }
    @Test fun startupDeletionAndMalformedSourcesFailClosed() {
        var p=project();val scene=p.assets.resolve("Scenes/start.scene.json")
        ProjectDocuments(p).save(scene,ProjectDocumentKind.Scene,"{}",emptyMap(),null) {}
        p=ProjectFiles.save(p,p.document.copy(startupScene=AssetIdentity.read(AssetIdentity.sidecar(scene))))
        val ops=ProjectAssetOperations(p)
        assertTrue(ops.references("Scenes").any { it.source=="Project startup scene" })
        assertFails { ops.delete("Scenes") }
        Files.writeString(p.assets.resolve("Scenes/broken.scene.json"),"broken")
        assertFails { ops.move("Textures","Data/Pictures") }
        assertTrue(Files.isDirectory(p.assets.resolve("Textures")))
    }
    @Test fun recoveryNeverOverwritesNewArrivals() {
        val p=project();val ops=ProjectAssetOperations(p);val file=p.assets.resolve("Data/a.txt")
        Files.writeString(file,"old");val receipt=ops.delete("Data/a.txt")
        Files.writeString(file,"new")
        assertFails { ops.restore(receipt) }
        assertEquals("new",Files.readString(file))
    }
    @Test fun recoverySurvivesRestartAndCanRestoreAfterProjectRelocation() {
        val p=project();val asset=p.assets.resolve("Data/a.txt");Files.writeString(asset,"payload")
        val id=AssetIdentity.ensure(asset);ProjectAssetOperations(p).delete("Data/a.txt")
        val moved=p.root.resolveSibling("Moved");Files.move(p.root,moved)
        val reopened=ProjectFiles.open(moved.resolve(p.file.fileName))
        val ops=ProjectAssetOperations(reopened);val receipt=ops.deletions().single()
        ops.restore(receipt)
        assertEquals("payload",Files.readString(reopened.assets.resolve("Data/a.txt")))
        assertEquals(id,AssetIdentity.read(reopened.assets.resolve("Data/a.txt.slemeta")))
        assertTrue(ops.deletions().isEmpty())
    }
    @Test fun managedSceneRenameCannotRemoveItsSourceSuffix() {
        val p=project();val source=p.assets.resolve("Scenes/a.scene.json")
        ProjectDocuments(p).save(source,ProjectDocumentKind.Scene,"{}",emptyMap(),null) {}
        assertFails { ProjectAssetOperations(p).move("Scenes/a.scene.json","Scenes/a.txt") }
        assertTrue(Files.exists(source))
    }

    @Test fun interruptedDeleteDoesNotMintANewIdentityBeforeRecovery() {
        val p=project();val asset=p.assets.resolve("Data/a.txt");Files.writeString(asset,"payload")
        val id=AssetIdentity.ensure(asset)
        val receipt=ProjectAssetOperations(p).delete("Data/a.txt")
        val storage=p.root.resolve(".sle/trash").resolve(receipt.token)
        // Crash after sidecar move but before the payload move.
        Files.move(storage.resolve("payload"),asset)
        val scan=AssetScanner.scan(p.root,p.assets)
        assertFalse(Files.exists(AssetIdentity.sidecar(asset)))
        assertTrue(scan.errors.any { it.contains("recovery",true) })
        ProjectAssetOperations(p).restore(receipt)
        assertEquals(id,AssetIdentity.read(AssetIdentity.sidecar(asset)))
    }
    @Test fun interruptedRestoreCanFinishAfterMetadataWasRestored() {
        val p=project();val asset=p.assets.resolve("Data/a.txt");Files.writeString(asset,"payload")
        val id=AssetIdentity.ensure(asset);val ops=ProjectAssetOperations(p);val receipt=ops.delete("Data/a.txt")
        val storage=p.root.resolve(".sle/trash").resolve(receipt.token)
        Files.move(storage.resolve("metadata"),AssetIdentity.sidecar(asset))
        ops.restore(receipt)
        assertEquals("payload",Files.readString(asset))
        assertEquals(id,AssetIdentity.read(AssetIdentity.sidecar(asset)))
    }

}
