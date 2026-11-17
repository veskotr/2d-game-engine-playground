package sle.editor.project.assets

import java.nio.file.*
import kotlin.test.*

class AssetIdentityTest {
    @Test fun ordinaryDotFilesRemainVisible() {
        val project=Files.createTempDirectory("sle-id-");val assets=Files.createDirectory(project.resolve("Assets"))
        Files.writeString(assets.resolve(".sle-write-settings.lua"),"return {}")
        val snapshot=AssetScanner.scan(project,assets)
        assertEquals(listOf(".sle-write-settings.lua"),snapshot.entries.map { it.path })
        assertEquals(1,snapshot.pathsById.size)
    }
    @Test fun identitySurvivesEditsMovesAndProjectRelocation() {
        val project=Files.createTempDirectory("sle-id-");val assets=Files.createDirectories(project.resolve("Assets"))
        val file=Files.writeString(assets.resolve("main.lua"),"return 1")
        val first=AssetScanner.scan(project,assets)
        val id=first.idsByPath.getValue("main.lua")
        Files.writeString(file,"return 2")
        assertEquals(id,AssetScanner.scan(project,assets).idsByPath["main.lua"])
        Files.move(file,assets.resolve("renamed.lua"))
        Files.move(AssetIdentity.sidecar(file),AssetIdentity.sidecar(assets.resolve("renamed.lua")))
        val moved=project.resolveSibling(project.fileName.toString()+"-moved")
        Files.move(project,moved)
        assertEquals("renamed.lua",AssetScanner.scan(moved,moved.resolve("Assets")).pathsById[id])
    }
    @Test fun ambiguousAndMalformedMetadataNeverResolvesAndRepairIsExplicit() {
        val project=Files.createTempDirectory("sle-id-");val assets=Files.createDirectory(project.resolve("Assets"))
        val a=Files.writeString(assets.resolve("a.lua"),"a")
        AssetScanner.scan(project,assets)
        val meta=Files.readString(AssetIdentity.sidecar(a))
        val b=Files.writeString(assets.resolve("b.lua"),"b")
        Files.writeString(AssetIdentity.sidecar(b),meta)
        val bad=Files.writeString(assets.resolve("bad.lua"),"bad")
        Files.writeString(AssetIdentity.sidecar(bad),"broken")
        val snapshot=AssetScanner.scan(project,assets)
        assertTrue(snapshot.pathsById.isEmpty());assertEquals(3,snapshot.entries.size)
        assertTrue(snapshot.errors.any { it.contains("Duplicate") });assertTrue(snapshot.errors.any { it.contains("bad.lua") })
        assertEquals("broken",Files.readString(AssetIdentity.sidecar(bad)))
        AssetIdentity.repair(project,assets,"b.lua")
        AssetIdentity.repair(project,assets,"bad.lua")
        assertEquals(3,AssetScanner.scan(project,assets).pathsById.size)
    }
}
