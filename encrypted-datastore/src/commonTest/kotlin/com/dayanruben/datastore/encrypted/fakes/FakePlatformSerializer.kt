package com.dayanruben.datastore.encrypted.fakes

import com.dayanruben.datastore.encrypted.PlatformInputStream
import com.dayanruben.datastore.encrypted.PlatformOutputStream
import com.dayanruben.datastore.encrypted.PlatformSerializer
import com.dayanruben.datastore.encrypted.platformReadBytes

// --- FakePlatformSerializer for ByteArray ---
class FakeByteArraySerializer(
    actual override val defaultValue: ByteArray = byteArrayOf()
) : PlatformSerializer<ByteArray> {

    var readFromCalled = 0
    var writeToCalled = 0

    actual override suspend fun readFrom(input: PlatformInputStream): ByteArray {
        readFromCalled++
        // Use the actual platformReadBytes which now has a commonTest actual
        val bytes = input.platformReadBytes()
        input.close() // Ensure stream is closed after reading
        return bytes
    }

    actual override suspend fun writeTo(t: ByteArray, output: PlatformOutputStream) {
        writeToCalled++
        // Use the actual platformWriteBytes which now has a commonTest actual
        output.platformWriteBytes(t)
        output.close() // Ensure stream is closed after writing
    }
}

// --- FakePlatformSerializer for String ---
class FakeStringSerializer(
    actual override val defaultValue: String = ""
) : PlatformSerializer<String> {

    var readFromCalled = 0
    var writeToCalled = 0

    actual override suspend fun readFrom(input: PlatformInputStream): String {
        readFromCalled++
        val bytes = input.platformReadBytes()
        input.close()
        return bytes.decodeToString()
    }

    actual override suspend fun writeTo(t: String, output: PlatformOutputStream) {
        writeToCalled++
        output.platformWriteBytes(t.encodeToByteArray())
        output.close()
    }
}
