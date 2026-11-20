package sle.editor.project.assets

import java.awt.Color
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.test.*
import org.junit.Rule
import org.junit.rules.TemporaryFolder

class AssetThumbnailTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun regeneratesAfterCacheDeletionAndRefreshesReplacedSource() {
        val project = temp.root.toPath()
        val assets = Files.createDirectory(project.resolve("Assets"))
        val source = assets.resolve("sprite.png")
        writeTexture(source, Color.RED)
        val cache = AssetThumbnailCache(project, assets)
        val first = assertNotNull(cache.load("sprite.png", null))
        assertEquals(Color.RED.rgb, first.getRGB(0, 0))
        assertEquals(first.width, first.height * 2)

        val directory = project.resolve(".sle/cache/thumbnails")
        Files.list(directory).use { Files.delete(it.toList().single()) }
        assertEquals(Color.RED.rgb, assertNotNull(cache.load("sprite.png", null)).getRGB(0, 0))
        Files.list(directory).use { assertEquals(1L, it.count()) }

        val previousTimestamp = Files.getLastModifiedTime(source)
        writeTexture(source, Color.BLUE)
        Files.setLastModifiedTime(source, previousTimestamp)
        assertEquals(Color.BLUE.rgb, assertNotNull(cache.load("sprite.png", null)).getRGB(0, 0))
    }

    @Test fun usesTheTextureFilterWhenReducingThePreview() {
        val project = temp.root.toPath()
        val assets = Files.createDirectory(project.resolve("Assets"))
        val source = assets.resolve("sprite.png")
        val image = BufferedImage(320, 160, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until image.height) for (x in 0 until image.width) {
            image.setRGB(x, y, if (x % 2 == 0) Color.BLACK.rgb else Color.WHITE.rgb)
        }
        ImageIO.write(image, "png", source.toFile())
        val cache = AssetThumbnailCache(project, assets)
        val nearest = assertNotNull(cache.load("sprite.png", AssetTextureFilter.Nearest))
        val linear = assertNotNull(cache.load("sprite.png", AssetTextureFilter.Linear))
        assertNotEquals(nearest.getRGB(0, 0), linear.getRGB(0, 0))
    }

    private fun writeTexture(path: Path, color: Color) {
        val image = BufferedImage(320, 160, BufferedImage.TYPE_INT_ARGB)
        val graphics = image.createGraphics()
        try {
            graphics.color = color
            graphics.fillRect(0, 0, image.width, image.height)
        } finally {
            graphics.dispose()
        }
        ImageIO.write(image, "png", path.toFile())
    }
}
