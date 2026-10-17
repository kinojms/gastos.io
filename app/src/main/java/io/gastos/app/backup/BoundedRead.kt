package io.gastos.app.backup

import java.io.ByteArrayOutputStream
import java.io.InputStream

/** Reads a picked file without trusting its size. (`readNBytes(int)` needs API 33; minSdk is 26.) */
object BoundedRead {
    /** All bytes of [input], or null as soon as it holds more than [max] bytes. */
    fun readAll(input: InputStream, max: Int): ByteArray? {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            if (out.size() + n > max) return null
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    }
}
