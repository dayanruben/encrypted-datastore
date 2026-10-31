package com.dayanruben.datastore.encrypted.kmp

import androidx.datastore.core.okio.OkioSerializer
import kotlinx.coroutines.test.runTest
import okio.Buffer
import okio.BufferedSink
import okio.BufferedSource
import java.nio.ByteBuffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class OkioCompatibilityTest {
    @Test
    fun preservesOutputStreamByteBufferAndCharsetWrites() = runTest {
        val cipher = aesGcmCipher(ByteArray(32))
        for (mode in 0..2) {
            val serializer = object : OkioSerializer<String> {
                override val defaultValue = ""
                override suspend fun readFrom(source: BufferedSource) = source.readUtf8()
                override suspend fun writeTo(t: String, sink: BufferedSink) {
                    when (mode) {
                        0 -> sink.outputStream().write(t.encodeToByteArray())
                        1 -> sink.write(ByteBuffer.wrap(t.encodeToByteArray()))
                        2 -> sink.writeString(t, Charsets.UTF_8)
                    }
                }
            }.encrypted(cipher, "settings", 64)
            val output = Buffer()
            serializer.writeTo("ã".repeat(32), output)
            assertEquals("ã".repeat(32), serializer.readFrom(output))
            assertFailsWith<PayloadTooLargeException> { serializer.writeTo("ã".repeat(33), output) }
            assertEquals(0L, output.size)
        }
    }
}
