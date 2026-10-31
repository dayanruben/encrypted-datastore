package com.dayanruben.datastore.encrypted.kmp

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class RealFileDataStoreTest {
    @Test
    fun reopensAnActualEncryptedFile() = runTest {
        val directory = Files.createTempDirectory("encrypted-datastore-kmp-test")
        val path = directory.resolve("settings.pb").toString().toPath()
        val firstScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val secondScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val cipher = aesGcmCipher(ByteArray(32))
            createEncryptedDataStore(Utf8Serializer, cipher, "settings", path, firstScope).updateData { "persisted-secret" }
            firstScope.coroutineContext.job.cancelAndJoin()
            assertFalse(Files.readString(directory.resolve("settings.pb"), Charsets.ISO_8859_1).contains("persisted-secret"))
            assertEquals("persisted-secret", createEncryptedDataStore(Utf8Serializer, cipher, "settings", path, secondScope).data.first())
        } finally {
            firstScope.coroutineContext.job.cancelAndJoin()
            secondScope.coroutineContext.job.cancelAndJoin()
            Files.walk(directory).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::delete) }
        }
    }
}
