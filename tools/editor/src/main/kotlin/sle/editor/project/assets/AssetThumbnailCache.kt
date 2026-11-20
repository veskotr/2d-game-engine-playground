package sle.editor.project.assets

import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.HexFormat
import javax.imageio.ImageIO
import kotlin.math.roundToInt

/** Derived, project-local previews. A cache failure can still yield an in-memory image. */
internal class AssetThumbnailCache(private val project: Path, private val assets: Path) {
    fun load(relative: String, filter: AssetTextureFilter?): BufferedImage? {
        if (!isTextureAsset(relative)) return null
        return try {
            val source = assetPath(project, assets, relative)
            val before = Files.readAttributes(source, BasicFileAttributes::class.java, NOFOLLOW_LINKS)
            if (!before.isRegularFile) return null
            val name = fingerprint(source, filter) + ".png"
            val cached = runCatching { cacheDirectory().resolve(name) }.getOrNull()
            val existing = cached?.let { path ->
                runCatching {
                    if (Files.isRegularFile(path, NOFOLLOW_LINKS)) readThumbnail(path, filter) else null
                }.getOrNull()
            }
            val preview = existing ?: readThumbnail(source, filter) ?: return null
            val after = Files.readAttributes(source, BasicFileAttributes::class.java, NOFOLLOW_LINKS)
            if (before.fileKey() != after.fileKey() || before.size() != after.size() ||
                before.lastModifiedTime() != after.lastModifiedTime()) return null
            if (existing != null || cached == null) return preview
            // A writer may preserve both size and timestamp while the image is decoded.
            if (fingerprint(source, filter) + ".png" != name) return null
            runCatching {
                val bytes = ByteArrayOutputStream()
                if (ImageIO.write(preview, "png", bytes)) atomicAssetWrite(cached, bytes.toByteArray(), replace = true)
            }
            preview
        } catch (_: IOException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private fun fingerprint(source: Path, filter: AssetTextureFilter?): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update("v1/$extent/${filter?.serialized ?: "default"}/".toByteArray(Charsets.UTF_8))
        DigestInputStream(Files.newInputStream(source, NOFOLLOW_LINKS), digest).use {
            it.transferTo(OutputStream.nullOutputStream())
        }
        return HexFormat.of().formatHex(digest.digest())
    }

    private fun cacheDirectory(): Path {
        var directory = project.toRealPath()
        for (part in listOf(".sle", "cache", "thumbnails")) {
            directory = directory.resolve(part)
            if (!Files.exists(directory, NOFOLLOW_LINKS)) Files.createDirectories(directory)
            require(!Files.isSymbolicLink(directory) && directory.toRealPath() == directory) {
                "Thumbnail storage points through a link"
            }
        }
        return directory
    }

    private fun readThumbnail(file: Path, filter: AssetTextureFilter?): BufferedImage? {
        Files.newInputStream(file, NOFOLLOW_LINKS).use { source ->
            val stream = ImageIO.createImageInputStream(source) ?: return null
            stream.use {
                val reader = ImageIO.getImageReaders(stream).asSequence().firstOrNull() ?: return null
                try {
                    reader.setInput(stream, true, true)
                    val width = reader.getWidth(0)
                    val height = reader.getHeight(0)
                    val scale = (extent.toDouble() / maxOf(width, height)).coerceAtMost(1.0)
                    val outputWidth = (width * scale).roundToInt().coerceAtLeast(1)
                    val outputHeight = (height * scale).roundToInt().coerceAtLeast(1)
                    val stride = (1 + (maxOf(width, height) - 1) / decodeExtent)
                    val params = reader.defaultReadParam.apply { setSourceSubsampling(stride, stride, 0, 0) }
                    val image = reader.read(0, params)
                    val preview = BufferedImage(outputWidth, outputHeight, BufferedImage.TYPE_INT_ARGB)
                    val graphics = preview.createGraphics()
                    try {
                        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                            if (filter == AssetTextureFilter.Nearest) RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR
                            else RenderingHints.VALUE_INTERPOLATION_BILINEAR)
                        graphics.drawImage(image, 0, 0, outputWidth, outputHeight, null)
                    } finally {
                        graphics.dispose()
                    }
                    return preview
                } finally {
                    reader.dispose()
                }
            }
        }
    }

    private companion object {
        const val extent = 96
        const val decodeExtent = 2048
    }
}
