package sle.editor.project

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Header fixture for inventory tests. Native package validation never executes it. */
internal fun runtimeFixtureLauncher(linux: Boolean = false): ByteArray {
    val header = ByteBuffer.allocate(96).order(ByteOrder.LITTLE_ENDIAN)
    if (linux) {
        header.putInt(0, 0x464c457f)
        header.put(4, 2) // ELF64
        header.put(5, 1) // Little endian
        header.put(6, 1) // ELF version
        header.putShort(16, 3) // ET_DYN (PIE)
        header.putShort(18, 62) // EM_X86_64
    } else {
        header.putShort(0, 0x5a4d)
        header.putInt(60, 64)
        header.putInt(64, 0x4550)
        header.putShort(68, 0x8664.toShort())
        header.putShort(84, 2) // Optional header size
        header.putShort(86, 2) // Executable image
        header.putShort(88, 0x20b) // PE32+
    }
    return header.array()
}
