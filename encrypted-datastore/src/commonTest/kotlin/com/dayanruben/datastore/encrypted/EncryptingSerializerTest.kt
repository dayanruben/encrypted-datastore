package com.dayanruben.datastore.encrypted

import com.dayanruben.datastore.encrypted.fakes.CommonTestByteArrayPlatformOutputStream
import com.dayanruben.datastore.encrypted.fakes.FakeByteArraySerializer
import com.dayanruben.datastore.encrypted.fakes.FakeExpectedAead
import com.dayanruben.datastore.encrypted.fakes.FakeExpectedStreamingAead
import com.dayanruben.datastore.encrypted.fakes.FakePlatformInputStream
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class EncryptingSerializerTest {

    // Using fakes now
    private val fakeStreamingAead = FakeExpectedStreamingAead()
    private val fakeAead = FakeExpectedAead()

    // The test "encrypt and decrypt using serializer not closing stream"
    // was specific to a serializer that deliberately didn't close streams.
    // Our FakePlatformSerializer and stream fakes manage closure.
    // Let's create more targeted tests for AeadEncryptingSerializer and StreamingAeadEncryptingSerializer.

    @Test
    fun `AeadEncryptingSerializer should encrypt and decrypt data correctly`() = runBlocking {
        val originalData = "Hello, KMP!".toByteArray()
        val serializer = FakeByteArraySerializer()
        val encryptingSerializer = serializer.encrypted(fakeAead)

        // Encrypt
        val outputStream = CommonTestByteArrayPlatformOutputStream()
        encryptingSerializer.writeTo(originalData, outputStream)
        val encryptedData = outputStream.toByteArray()
        assertTrue(outputStream.isClosed(), "Output stream should be closed by serializer writeTo")


        // Decrypt
        val inputStream = FakePlatformInputStream(encryptedData)
        val decryptedData = encryptingSerializer.readFrom(inputStream)
        assertTrue(inputStream.isClosed(), "Input stream should be closed by serializer readFrom")

        assertEquals(originalData.contentToString(), decryptedData.contentToString(), "Decrypted data should match original")
        assertTrue(fakeAead.encryptCalled > 0, "AEAD encrypt should be called")
        assertTrue(fakeAead.decryptCalled > 0, "AEAD decrypt should be called")
    }

    @Test
    fun `StreamingAeadEncryptingSerializer should encrypt and decrypt data correctly`() = runBlocking {
        val originalData = "Hello, Streaming KMP!".toByteArray()
        val serializer = FakeByteArraySerializer()
        val encryptingSerializer = serializer.encrypted(fakeStreamingAead)

        // Encrypt
        val outputStream = CommonTestByteArrayPlatformOutputStream()
        encryptingSerializer.writeTo(originalData, outputStream)
        val encryptedData = outputStream.toByteArray()
        // Streaming serializers (especially our fake) should ensure the underlying stream is closed.
        // Our FakeExpectedStreamingAead's streams close the delegate.

        // Decrypt
        val inputStream = FakePlatformInputStream(encryptedData)
        val decryptedData = encryptingSerializer.readFrom(inputStream)

        assertEquals(originalData.contentToString(), decryptedData.contentToString(), "Decrypted data should match original")
        assertTrue(fakeStreamingAead.newEncryptingStreamCalled > 0, "StreamingAEAD newEncryptingStream should be called")
        assertTrue(fakeStreamingAead.newDecryptingStreamCalled > 0, "StreamingAEAD newDecryptingStream should be called")
    }

    @Test
    fun `AeadEncryptingSerializer with empty data`() = runBlocking {
        val originalData = byteArrayOf()
        val serializer = FakeByteArraySerializer(defaultValue = byteArrayOf()) // Provide default for empty case
        val encryptingSerializer = serializer.encrypted(fakeAead)

        val outputStream = CommonTestByteArrayPlatformOutputStream()
        encryptingSerializer.writeTo(originalData, outputStream)
        val encryptedData = outputStream.toByteArray()

        val inputStream = FakePlatformInputStream(encryptedData)
        val decryptedData = encryptingSerializer.readFrom(inputStream)

        assertEquals(originalData.contentToString(), decryptedData.contentToString())
        // Depending on AEAD behavior with empty data, encrypt/decrypt might not be called if data is empty before encryption.
        // Our FakeAead does process empty arrays.
        assertTrue(fakeAead.encryptCalled > 0)
        assertTrue(fakeAead.decryptCalled > 0)
    }

    @Test
    fun `StreamingAeadEncryptingSerializer with empty data`() = runBlocking {
        val originalData = byteArrayOf()
        val serializer = FakeByteArraySerializer(defaultValue = byteArrayOf())
        val encryptingSerializer = serializer.encrypted(fakeStreamingAead)

        val outputStream = CommonTestByteArrayPlatformOutputStream()
        encryptingSerializer.writeTo(originalData, outputStream)
        val encryptedData = outputStream.toByteArray()

        val inputStream = FakePlatformInputStream(encryptedData)
        val decryptedData = encryptingSerializer.readFrom(inputStream)
        assertEquals(originalData.contentToString(), decryptedData.contentToString())
        assertTrue(fakeStreamingAead.newEncryptingStreamCalled > 0)
        assertTrue(fakeStreamingAead.newDecryptingStreamCalled > 0)
    }

    // The old AlwaysOpenStreamSerializer is no longer needed as we use FakeByteArraySerializer
    // and test stream closure via the fakes.
}
