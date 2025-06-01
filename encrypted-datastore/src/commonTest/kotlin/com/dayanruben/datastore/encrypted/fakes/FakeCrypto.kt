package com.dayanruben.datastore.encrypted.fakes

import com.dayanruben.datastore.encrypted.ExpectedAead
import com.dayanruben.datastore.encrypted.ExpectedStreamingAead
import com.dayanruben.datastore.encrypted.PlatformInputStream
import com.dayanruben.datastore.encrypted.PlatformOutputStream
import com.dayanruben.datastore.encrypted.CommonGeneralSecurityException

// --- FakeExpectedAead ---
class FakeExpectedAead(private val key: Byte = 0x42) : ExpectedAead {
    var encryptCalled = 0
    var decryptCalled = 0

    // Simple XOR "encryption" for testing
    override fun encrypt(plaintext: ByteArray, associatedData: ByteArray?): ByteArray {
        encryptCalled++
        if (plaintext.isEmpty()) return ByteArray(0) // Some AEADs fail on empty, some don't
        return plaintext.map { (it.toInt() xor key.toInt()).toByte() }.toByteArray()
    }

    override fun decrypt(ciphertext: ByteArray, associatedData: ByteArray?): ByteArray {
        decryptCalled++
        if (ciphertext.isEmpty()) return ByteArray(0)
        // XORing again decrypts
        return ciphertext.map { (it.toInt() xor key.toInt()).toByte() }.toByteArray()
    }
}

// --- FakeExpectedStreamingAead ---
class FakeExpectedStreamingAead(private val key: Byte = 0x42) : ExpectedStreamingAead {
    var newEncryptingStreamCalled = 0
    var newDecryptingStreamCalled = 0

    override fun newEncryptingStream(ciphertextDestination: PlatformOutputStream, associatedData: ByteArray): PlatformOutputStream {
        newEncryptingStreamCalled++
        // Returns a stream that "encrypts" by XORing and writes to ciphertextDestination
        return object : PlatformOutputStream() { // Anonymous class extending PlatformOutputStream
            override fun write(b: Int) {
                ciphertextDestination.write(b xor key.toInt())
            }

            override fun write(b: ByteArray, off: Int, len: Int) {
                val encryptedChunk = b.sliceArray(off until off + len).map { (it.toInt() xor key.toInt()).toByte() }.toByteArray()
                ciphertextDestination.write(encryptedChunk, 0, encryptedChunk.size)
            }

            override fun flush() {
                ciphertextDestination.flush()
            }

            override fun close() {
                ciphertextDestination.close()
            }
        }
    }

    override fun newDecryptingStream(ciphertextSource: PlatformInputStream, associatedData: ByteArray): PlatformInputStream {
        newDecryptingStreamCalled++
        // Returns a stream that "decrypts" by XORing data read from ciphertextSource
        return object : PlatformInputStream() { // Anonymous class extending PlatformInputStream
            override fun read(): Int {
                val encryptedByte = ciphertextSource.read()
                return if (encryptedByte != -1) encryptedByte xor key.toInt() else -1
            }

            override fun read(b: ByteArray, off: Int, len: Int): Int {
                val tempBuffer = ByteArray(len)
                val bytesRead = ciphertextSource.read(tempBuffer, 0, len)
                if (bytesRead == -1) return -1
                for (i in 0 until bytesRead) {
                    b[off + i] = (tempBuffer[i].toInt() xor key.toInt()).toByte()
                }
                return bytesRead
            }

            override fun available(): Int = ciphertextSource.available()
            override fun close() = ciphertextSource.close()
        }
    }
}
