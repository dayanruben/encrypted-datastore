package com.dayanruben.datastore.encrypted

import androidx.datastore.core.Serializer
import com.google.crypto.tink.StreamingAead
import com.google.crypto.tink.streamingaead.StreamingAeadConfig
import com.google.crypto.tink.aead.AeadConfig
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayOutputStream
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.OutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertContentEquals

internal class EncryptingSerializerTest {

    private var streamingAead: StreamingAead

    init {
        StreamingAeadConfig.register()

        streamingAead = TestAssets.generateStreamingAead()
    }

    @Test
    fun `encrypt and decrypt using serializer not closing stream`() {
        val plaintext = "Plaintext".toByteArray()
        val encryptingSerializer = AlwaysOpenStreamSerializer.encrypted(streamingAead)

        runBlocking {
            val bytesOutputStream = ByteArrayOutputStream()
            encryptingSerializer.writeTo(plaintext, bytesOutputStream)

            val bytes = bytesOutputStream.toByteArray()
            val decrypted = encryptingSerializer.readFrom(bytes.inputStream())

            assertEquals(plaintext.contentToString(), decrypted.contentToString())
        }
    }

    @Test
    @Suppress("DEPRECATION")
    fun `legacy AEAD decrypts a stream whose available count is zero`() = runBlocking {
        AeadConfig.register()
        val aead = TestAssets.generateAead()
        val plaintext = "Previously stored settings".toByteArray()
        val ciphertext = aead.encrypt(plaintext, null)
        val source = object : ByteArrayInputStream(ciphertext) {
            override fun available(): Int = 0
        }
        assertContentEquals(plaintext, AlwaysOpenStreamSerializer.encrypted(aead).readFrom(source))
    }

    @Test
    fun `streaming associated data is snapshotted at construction`() = runBlocking {
        val associatedData = "settings".toByteArray()
        val originalData = associatedData.copyOf()
        val serializer = AlwaysOpenStreamSerializer.encrypted(streamingAead, associatedData)
        val plaintext = byteArrayOf(1, 2, 3)
        val first = ByteArrayOutputStream().also { serializer.writeTo(plaintext, it) }.toByteArray()
        associatedData.fill(0)
        assertContentEquals(plaintext, serializer.readFrom(first.inputStream()))
        val second = ByteArrayOutputStream().also { serializer.writeTo(plaintext, it) }.toByteArray()
        assertContentEquals(plaintext, streamingAead.newDecryptingStream(second.inputStream(), originalData).use { it.readBytes() })
    }

    private object AlwaysOpenStreamSerializer : Serializer<ByteArray> {
        override val defaultValue: ByteArray = byteArrayOf()
        override suspend fun readFrom(input: InputStream): ByteArray = input.readBytes()
        override suspend fun writeTo(t: ByteArray, output: OutputStream) = output.write(t)
    }
}
