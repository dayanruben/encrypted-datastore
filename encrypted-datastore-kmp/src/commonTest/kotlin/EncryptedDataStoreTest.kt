package com.dayanruben.datastore.encrypted.kmp

import androidx.datastore.core.DataMigration
import kotlinx.coroutines.async
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okio.ForwardingFileSystem
import okio.IOException
import okio.Path
import okio.Path.Companion.toPath
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@OptIn(ExperimentalCoroutinesApi::class)
class EncryptedDataStoreTest {
    private val path = "/settings.pb".toPath()

    @Test
    fun persistsAndReopensWithAnExplicitLifetime() = runTest {
        withStoreFiles {
            val cipher = aesGcmCipher(ByteArray(32))
            val firstScope = newScope()
            val first = createEncryptedDataStore(Utf8Serializer, cipher, "settings", path, firstScope, fileSystem)
            assertEquals("", first.data.first())
            first.updateData { "persisted" }
            firstScope.coroutineContext.job.cancelAndJoin()
            val reopened = createEncryptedDataStore(Utf8Serializer, cipher, "settings", path, newScope(), fileSystem)
            assertEquals("persisted", reopened.data.first())
        }
    }

    @Test
    fun serializesOneHundredConcurrentUpdates() = runTest {
        withStoreFiles {
            val store = createEncryptedDataStore(Utf8Serializer, aesGcmCipher(ByteArray(32)), "counter", path, newScope(), fileSystem)
            (0 until 100).map { async { store.updateData { ((it.toIntOrNull() ?: 0) + 1).toString() } } }.awaitAll()
            assertEquals("100", store.data.first())
        }
    }

    @Test
    fun failedTransformAndSerializationPreserveThePriorRecord() = runTest {
        withStoreFiles {
            val cipher = aesGcmCipher(ByteArray(32))
            val failure = IOException("test write failure")
            val serializer = object : RecordSerializer<String> by Utf8Serializer {
                override suspend fun writeTo(t: String, writer: RecordWriter) {
                    writer.writeUtf8(t)
                    if (t == "fail") throw failure
                }
            }
            val scope = newScope()
            val store = createEncryptedDataStore(serializer, cipher, "settings", path, scope, fileSystem)
            store.updateData { "before" }
            val before = fileSystem.read(path) { readByteArray() }
            assertFailsWith<IllegalStateException> { store.updateData { error("test transform failure") } }
            assertFailsWith<IOException> { store.updateData { "fail" } }
            assertContentEquals(before, fileSystem.read(path) { readByteArray() })
            scope.coroutineContext.job.cancelAndJoin()
            assertEquals("before", createEncryptedDataStore(Utf8Serializer, cipher, "settings", path, newScope(), fileSystem).data.first())
        }
    }

    @Test
    fun cancelledTransformPreservesThePriorRecord() = runTest {
        withStoreFiles {
            val cipher = aesGcmCipher(ByteArray(32))
            val scope = newScope()
            val store = createEncryptedDataStore(Utf8Serializer, cipher, "settings", path, scope, fileSystem)
            store.updateData { "before" }
            val update = launch { store.updateData { awaitCancellation() } }
            runCurrent()
            update.cancelAndJoin()
            scope.coroutineContext.job.cancelAndJoin()
            assertEquals("before", createEncryptedDataStore(Utf8Serializer, cipher, "settings", path, newScope(), fileSystem).data.first())
        }
    }

    @Test
    fun failedAtomicReplacementPreservesThePriorRecord() = runTest {
        withStoreFiles {
            var rejectMoves = false
            val faulting = object : ForwardingFileSystem(fileSystem) {
                override fun atomicMove(source: Path, target: Path) {
                    if (rejectMoves) throw IOException("test atomic move failure")
                    super.atomicMove(source, target)
                }
            }
            val cipher = aesGcmCipher(ByteArray(32))
            val scope = newScope()
            val store = createEncryptedDataStore(Utf8Serializer, cipher, "settings", path, scope, faulting)
            store.updateData { "before" }
            rejectMoves = true
            assertFailsWith<IOException> { store.updateData { "after" } }
            scope.coroutineContext.job.cancelAndJoin()
            assertEquals("before", createEncryptedDataStore(Utf8Serializer, cipher, "settings", path, newScope(), fileSystem).data.first())
        }
    }

    @Test
    fun wrongKeyAndEmptyExistingFileAreNeverReplaced() = runTest {
        withStoreFiles {
            val scope = newScope()
            val store = createEncryptedDataStore(Utf8Serializer, aesGcmCipher(ByteArray(32)), "settings", path, scope, fileSystem)
            store.updateData { "before" }
            scope.coroutineContext.job.cancelAndJoin()
            val original = fileSystem.read(path) { readByteArray() }
            val wrongScope = newScope()
            val wrong = createEncryptedDataStore(Utf8Serializer, aesGcmCipher(ByteArray(32) { 1 }), "settings", path, wrongScope, fileSystem)
            assertFailsWith<AuthenticationException> { wrong.data.first() }
            assertContentEquals(original, fileSystem.read(path) { readByteArray() })
            wrongScope.coroutineContext.job.cancelAndJoin()
            fileSystem.write(path) { }
            val empty = createEncryptedDataStore(Utf8Serializer, aesGcmCipher(ByteArray(32)), "settings", path, newScope(), fileSystem)
            assertFailsWith<UnsupportedFormatException> { empty.data.first() }
            assertEquals(0L, fileSystem.metadata(path).size)
        }
    }

    @Test
    fun requiresAnAbsolutePath() = runTest {
        withStoreFiles {
            assertFailsWith<IllegalArgumentException> {
                createEncryptedDataStore(Utf8Serializer, aesGcmCipher(ByteArray(32)), "settings", "relative.pb".toPath(), newScope(), fileSystem)
            }
        }
    }

    @Test
    fun runsMigrationsBeforeReturningData() = runTest {
        withStoreFiles {
            val cipher = aesGcmCipher(ByteArray(32))
            val initialScope = newScope()
            createEncryptedDataStore(Utf8Serializer, cipher, "settings", path, initialScope, fileSystem).updateData { "v1" }
            initialScope.coroutineContext.job.cancelAndJoin()
            var cleanups = 0
            val migration = object : DataMigration<String> {
                override suspend fun shouldMigrate(currentData: String) = currentData == "v1"
                override suspend fun migrate(currentData: String) = "v2"
                override suspend fun cleanUp() { cleanups++ }
            }
            val migratedScope = newScope()
            val migrated = createEncryptedDataStore(Utf8Serializer, cipher, "settings", path, migratedScope, fileSystem, listOf(migration))
            assertEquals("v2", migrated.data.first())
            assertEquals(1, cleanups)
            migratedScope.coroutineContext.job.cancelAndJoin()
            val reopened = createEncryptedDataStore(Utf8Serializer, cipher, "settings", path, newScope(), fileSystem, listOf(migration))
            assertEquals("v2", reopened.data.first())
            assertEquals(1, cleanups)
        }
    }
}
