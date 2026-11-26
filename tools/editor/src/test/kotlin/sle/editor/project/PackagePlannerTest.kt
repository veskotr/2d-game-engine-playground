package sle.editor.project

import java.nio.file.Files
import java.util.UUID
import kotlin.test.*
import sle.editor.project.assets.AssetIdentity

class PackagePlannerTest {
    private fun asset(id: String, vararg refs: String, path: String = id) =
        PackageAsset(id, path, listOf("$path.slemeta"), refs.map { PackageReference(it) })

    @Test
    fun sharedTemplateAndTextureAreAssignedOnceEvenWithCycles() {
        val assets = listOf(
            asset("forest", "forestGroup", "fern"), asset("castle", "castleGroup", "banner"),
            asset("forestGroup", "enemy"), asset("castleGroup", "enemy"),
            asset("enemy", "texture", "forestGroup"), asset("texture"), asset("fern"), asset("banner"),
        )
        val scenes = mapOf("forest" to "Forest", "castle" to "Castle")
        val plan = PackagePlanner.plan(assets, scenes)
        assertTrue(plan.errors.isEmpty())
        assertEquals("forest", plan.owners["fern"]?.sceneId)
        assertEquals("castle", plan.owners["banner"]?.sceneId)
        assertEquals(PackageOwner.Shared, plan.owners["enemy"])
        assertEquals(PackageOwner.Shared, plan.owners["texture"])
        assertEquals(1, plan.packages.flatMap { it.paths }.count { it == "texture.slemeta" })
        assertEquals(plan, PackagePlanner.plan(assets.reversed(), scenes.toList().reversed().toMap()))

        val renamed = PackagePlanner.plan(assets.map {
            if (it.id == "forest") asset("forest", "forestGroup", "fern", path = "renamed.scene.json") else it
        }, scenes + ("forest" to "Renamed"), plan.owners)
        assertEquals(plan.packages.map { it.owner }, renamed.packages.map { it.owner })
        assertEquals(plan.owners, renamed.owners)
        val removed = PackagePlanner.plan(assets.filter { it.id != "castle" }, scenes - "castle", plan.owners)
        assertEquals(PackageOwner.Shared, removed.owners["enemy"])
        assertEquals(PackageOwner.Shared, removed.owners["texture"])
        assertEquals(PackageOwner.Shared, removed.owners["banner"])
    }

    @Test
    fun priorOwnerSticksUntilACompatibilityOrUsageChange() {
        val scenes = mapOf("forest" to "Forest", "castle" to "Castle")
        val assets = listOf(asset("forest", "prop", "atlas"), asset("castle", "image"),
            asset("prop"), asset("atlas").copy(references = listOf(PackageReference("image", colocate = true))),
            asset("image"))
        val prior = mapOf("prop" to PackageOwner("castle"))
        val plan = PackagePlanner.plan(assets, scenes, prior)
        assertEquals(PackageOwner("castle"), plan.owners["prop"])
        assertEquals(PackageOwner.Shared, plan.owners["atlas"])
        assertEquals(plan.owners["atlas"], plan.owners["image"])
        val reassigned = PackagePlanner.plan(assets.filter { it.id != "castle" }, scenes - "castle", prior)
        assertEquals(PackageOwner("forest"), reassigned.owners["prop"])
        val promoted = PackagePlanner.plan(assets.map { if (it.id == "castle") asset("castle", "prop") else it }, scenes, prior)
        assertEquals(PackageOwner.Shared, promoted.owners["prop"])
        val oneConsumer = PackagePlanner.plan(assets, scenes, promoted.owners)
        assertEquals(PackageOwner.Shared, oneConsumer.owners["prop"])
    }

    @Test
    fun savedInventoryTraversesNativeTilesOverridesAndDeclaredAssets() {
        val root = Files.createTempDirectory("sle-planner-").toRealPath()
        try {
            fun id(n: Long) = UUID(0, n).toString()
            fun save(path: String, n: Long, text: String) {
                Files.writeString(root.resolve(path), text)
                Files.writeString(root.resolve("$path.slemeta"), AssetIdentity.encode(id(n)))
            }
            save("Forest.scene.json", 1, """
                {"format":"sle-project-scene","version":1,"document":{"entities":[],"prefabs":[
                    {"file":"project-asset://${id(4)}","key":"enemy","overrides":{"enemy":{"SpriteRenderer":{"texture":"project-asset://${id(5)}"},"Script":{"behaviours":[{"parameters":{"sound":"project-asset://${id(6)}","label":"plain text"}}]}}}}
                ],"tileLayers":[{"tileset":"project-asset://${id(2)}"}]},"assets":["${id(7)}"]}
            """.trimIndent())
            save("terrain.data", 2, """{"format":"sle-tileset","version":1,"spritesheet":"project-asset://${id(3)}"}""")
            save("sheet.png", 3, "")
            save("Enemy.prefab.json", 4, """{"format":"sle-project-prefab","version":1,"document":{"root":"enemy","entities":[]},"assets":[]}""")
            save("enemy.png", 5, "")
            save("manual.txt", 7, "")
            save("module.lua", 8, "")
            Files.createDirectory(root.resolve("maps"))
            save("maps/level.tmj", 9, """{"tilesets":[{"image":"../map.png"}]}""")
            save("map.png", 10, "")
            save("Castle.scene.json", 11, """
                {"format":"sle-project-scene","version":1,"document":{"entities":[]},"assets":["${id(9)}"]}
            """.trimIndent())
            val inventory = SavedPackageInventory.read(root)
            val plan = PackagePlanner.plan(inventory.assets, inventory.scenes)
            for (n in listOf(2L, 3L, 4L, 5L, 7L)) assertEquals(PackageOwner(id(1)), plan.owners[id(n)])
            assertEquals(PackageOwner.Shared, plan.owners[id(8)])
            assertEquals(PackageOwner(id(11)), plan.owners[id(9)])
            assertEquals(PackageOwner(id(11)), plan.owners[id(10)])
            assertEquals(PackageOwnershipReason.Colocation, plan.reasons[id(10)])
            val rebuilt = PackagePlanner.plan(inventory.assets, inventory.scenes, plan.owners)
            assertEquals(plan.packages, rebuilt.packages)
            assertEquals(plan.owners, rebuilt.owners)
            assertEquals(listOf(PackageDependencyError("Forest.scene.json", "project-asset://${id(6)}")), plan.errors)
        } finally {
            ProjectDistribution.removeTree(root)
        }
    }
}
