package sle.editor.project

import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.CREATE_NEW
import java.nio.file.StandardOpenOption.WRITE
import java.nio.file.attribute.PosixFilePermissions
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/** The Linux launcher mode comes from the runtime layout, even on hosts without POSIX permissions. */
internal object ExportArchive {
    private const val BLOCK_SIZE = 512
    private const val FILE_MODE = 420 // 0644
    private const val EXECUTABLE_MODE = 493 // 0755

    fun makeExecutable(path: Path) {
        if ("posix" in path.fileSystem.supportedFileAttributeViews()) {
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rwxr-xr-x"))
        }
    }

    fun write(
        stage: Path,
        delivery: Path,
        target: GameTarget,
        executable: String,
        inventory: ProjectDistribution.Inventory,
    ): Path {
        val archive = stage.resolve(target.id + if (target == GameTarget.Windows) ".zip" else ".tar.gz")
        val prefix = target.id
        val folders = listOf("$prefix/") + inventory.folders.map { "$prefix/$it/" }
        if (target == GameTarget.Windows) {
            ZipOutputStream(Files.newOutputStream(archive, CREATE_NEW, WRITE)).use { zip ->
                for (folder in folders) {
                    zip.putNextEntry(ZipEntry(folder))
                    zip.closeEntry()
                }
                for (path in inventory.files.keys) {
                    zip.putNextEntry(ZipEntry("$prefix/$path"))
                    Files.copy(delivery.resolve(path), zip)
                    zip.closeEntry()
                }
            }
            ZipFile(archive.toFile()).use { zip ->
                val expected = folders.toSet() + inventory.files.keys.map { "$prefix/$it" }
                require(zip.size() == expected.size && zip.entries().toList().map { it.name }.toSet() == expected) {
                    "Invalid Windows export archive"
                }
                for ((path, hash) in inventory.files) {
                    zip.getInputStream(zip.getEntry("$prefix/$path")).use { checkHash(it, Long.MAX_VALUE, hash) }
                }
            }
        } else {
            writeTar(archive, delivery, prefix, folders, executable, inventory)
        }
        return archive
    }

    private fun writeTar(
        archive: Path,
        delivery: Path,
        prefix: String,
        folders: List<String>,
        executable: String,
        inventory: ProjectDistribution.Inventory,
    ) {
        val sizes = inventory.files.keys.associateWith { Files.size(delivery.resolve(it)) }
        val end = ByteArray(BLOCK_SIZE * 2)
        GZIPOutputStream(Files.newOutputStream(archive, CREATE_NEW, WRITE)).buffered().use { tar ->
            for (folder in folders) tar.write(tarHeaders(folder, EXECUTABLE_MODE, 0, '5'))
            for ((path, size) in sizes) {
                tar.write(tarHeaders("$prefix/$path", if (path == executable) EXECUTABLE_MODE else FILE_MODE, size, '0'))
                require(Files.copy(delivery.resolve(path), tar) == size) { "Export file changed: $path" }
                tar.write(padding(size))
            }
            tar.write(end)
        }
        GZIPInputStream(Files.newInputStream(archive)).buffered().use { tar ->
            fun verify(bytes: ByteArray) {
                require(tar.readNBytes(bytes.size).contentEquals(bytes)) { "Invalid Linux export archive" }
            }
            for (folder in folders) verify(tarHeaders(folder, EXECUTABLE_MODE, 0, '5'))
            for ((path, size) in sizes) {
                verify(tarHeaders("$prefix/$path", if (path == executable) EXECUTABLE_MODE else FILE_MODE, size, '0'))
                checkHash(tar, size, inventory.files.getValue(path))
                verify(padding(size))
            }
            verify(end)
            require(tar.read() == -1) { "Unexpected data in Linux export archive" }
        }
    }

    private fun checkHash(input: InputStream, size: Long, expected: String) {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(128 * 1024)
        var remaining = size
        while (remaining > 0) {
            val count = input.read(buffer, 0, minOf(remaining, buffer.size.toLong()).toInt())
            if (count < 0) break
            digest.update(buffer, 0, count)
            remaining -= count
        }
        require((size == Long.MAX_VALUE || remaining == 0L) && digest.digest().toHexString() == expected) {
            "Export archive file differs from its recorded SHA-256"
        }
    }

    private fun padding(size: Long) = ByteArray(((BLOCK_SIZE - size % BLOCK_SIZE) % BLOCK_SIZE).toInt())

    // POSIX pax path records support UTF-8 and paths longer than the ustar name field.
    private fun tarHeaders(name: String, mode: Int, size: Long, type: Char): ByteArray {
        if (name.length <= 100 && name.all { it.code < 128 }) return tarHeader(name, mode, size, type)
        val value = " path=$name\n"
        val length = value.encodeToByteArray().size
        var recordLength = length + 1
        while (recordLength != length + recordLength.toString().length) {
            recordLength = length + recordLength.toString().length
        }
        val record = "$recordLength$value".encodeToByteArray()
        return tarHeader("PaxHeaders/entry", FILE_MODE, record.size.toLong(), 'x') +
            record + padding(record.size.toLong()) + tarHeader("entry", mode, size, type)
    }

    private fun tarHeader(name: String, mode: Int, size: Long, type: Char): ByteArray {
        val header = ByteArray(BLOCK_SIZE)
        fun field(offset: Int, value: String) = value.encodeToByteArray().copyInto(header, offset)
        fun number(offset: Int, width: Int, value: Long) {
            val octal = value.toString(8)
            require(octal.length < width) { "File exceeds the TAR size limit: $name" }
            field(offset, octal.padStart(width - 1, '0'))
        }
        field(0, name)
        number(100, 8, mode.toLong())
        number(108, 8, 0)
        number(116, 8, 0)
        number(124, 12, size)
        number(136, 12, 0)
        field(148, "        ")
        field(156, type.toString())
        field(257, "ustar\u0000")
        field(263, "00")
        val checksum = header.sumOf { it.toUByte().toInt() }
        field(148, checksum.toString(8).padStart(6, '0') + "\u0000 ")
        return header
    }
}
