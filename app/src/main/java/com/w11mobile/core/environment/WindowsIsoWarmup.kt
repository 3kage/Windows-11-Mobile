package com.w11mobile.core.environment

import java.io.File
import java.io.RandomAccessFile

/** Sequential read of ISO head/tail to warm the kernel page cache before UEFI boot. */
object WindowsIsoWarmup {
    private const val CHUNK_BYTES = 4 * 1024 * 1024
    private const val HEAD_CHUNKS = 64 // ~256 MB
    private const val TAIL_CHUNKS = 16 // ~64 MB

    fun warm(isoFile: File, onLog: ((String) -> Unit)? = null) {
        if (!isoFile.isFile) return
        val size = isoFile.length()
        if (size <= 0L) return

        onLog?.invoke(">>> Прогрів кешу ISO (прискорення UDF-завантаження)…\n")
        RandomAccessFile(isoFile, "r").use { raf ->
            val buffer = ByteArray(CHUNK_BYTES)
            repeat(HEAD_CHUNKS) { index ->
                val offset = index.toLong() * CHUNK_BYTES
                if (offset >= size) return@repeat
                raf.seek(offset)
                raf.read(buffer)
            }
            repeat(TAIL_CHUNKS) { index ->
                val offset = size - (index + 1).toLong() * CHUNK_BYTES
                if (offset < 0L) return@repeat
                raf.seek(offset)
                raf.read(buffer)
            }
        }
        onLog?.invoke(">>> Прогрів ISO завершено.\n")
    }
}
