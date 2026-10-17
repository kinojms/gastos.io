package io.gastos.app.backup

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream

class BoundedReadTest {
    @Test fun readsASmallStreamFully() {
        val data = ByteArray(10) { it.toByte() }
        assertArrayEquals(data, BoundedRead.readAll(ByteArrayInputStream(data), max = 100))
    }

    @Test fun acceptsExactlyTheMax() {
        val data = ByteArray(100)
        assertArrayEquals(data, BoundedRead.readAll(ByteArrayInputStream(data), max = 100))
    }

    @Test fun refusesOneByteOverTheMaxWithoutReadingTheRest() {
        var served = 0
        val endless = object : InputStream() {
            override fun read(): Int { served++; return 1 }
            override fun read(b: ByteArray, off: Int, len: Int): Int { served += len; return len }
        }
        assertNull(BoundedRead.readAll(endless, max = 100))
        assert(served < 100_000) { "read $served bytes" }
    }
}
