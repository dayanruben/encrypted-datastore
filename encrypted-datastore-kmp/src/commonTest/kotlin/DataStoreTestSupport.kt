package com.dayanruben.datastore.encrypted.kmp

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import okio.fakefilesystem.FakeFileSystem

internal class StoreTestContext(private val testScope: TestScope) {
    val fileSystem = FakeFileSystem()
    private val scopes = mutableListOf<CoroutineScope>()
    fun newScope(): CoroutineScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScope.testScheduler))
        .also { scopes += it }

    suspend fun close() {
        scopes.forEach { it.coroutineContext.job.cancelAndJoin() }
        fileSystem.checkNoOpenFiles()
        fileSystem.close()
    }
}

internal suspend fun TestScope.withStoreFiles(block: suspend StoreTestContext.() -> Unit) {
    val context = StoreTestContext(this)
    try { context.block() } finally { context.close() }
}
