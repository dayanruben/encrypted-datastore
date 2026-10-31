package com.dayanruben.datastore.encrypted.kmp

import com.sun.management.ThreadMXBean
import kotlinx.coroutines.test.runTest
import okio.Buffer
import okio.BufferedSource
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.lang.management.ManagementFactory
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RecordWriterAllocationTest {
    @Test
    fun rejectingABulkWriteDoesNotCopyTheOversizedArgument() = runTest {
        val bean = ManagementFactory.getThreadMXBean() as? ThreadMXBean
        assumeTrue(bean?.isThreadAllocatedMemorySupported == true, "JVM thread allocation counters required")
        val allocations = requireNotNull(bean)
        allocations.isThreadAllocatedMemoryEnabled = true
        val argument = ByteArray(16_777_216) // Caller allocation, outside the measured write.
        var writeAllocation = 0L
        val serializer = object : RecordSerializer<Unit> {
            override val defaultValue = Unit
            override suspend fun readFrom(source: BufferedSource) = Unit
            override suspend fun writeTo(t: Unit, writer: RecordWriter) {
                val thread = Thread.currentThread().threadId()
                val before = allocations.getThreadAllocatedBytes(thread)
                try { writer.write(argument) } finally {
                    writeAllocation = allocations.getThreadAllocatedBytes(thread) - before
                }
            }
        }.encrypted(aesGcmCipher(ByteArray(32)), "settings", 64)
        // Warm class/exception initialization, then measure a fresh writer. No timing threshold.
        repeat(2) { assertFailsWith<PayloadTooLargeException> { serializer.writeTo(Unit, Buffer()) } }
        assertTrue(writeAllocation < 131_072, "A rejected write allocated $writeAllocation bytes")
    }
}
