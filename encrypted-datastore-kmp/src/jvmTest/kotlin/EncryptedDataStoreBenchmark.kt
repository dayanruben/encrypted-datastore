package com.dayanruben.datastore.encrypted.kmp

import com.sun.management.ThreadMXBean
import kotlinx.coroutines.runBlocking
import okio.Buffer
import java.lang.management.ManagementFactory
import java.nio.file.Files
import java.nio.file.Path
import javax.crypto.Cipher

/** Opt-in executable, deliberately excluded from ordinary test timing gates. */
object EncryptedDataStoreBenchmark {
    @JvmStatic
    fun main(args: Array<String>) = runBlocking {
        val report = mutableListOf<String>()
        fun record(line: String) { report += line; println(line) }
        record("JVM=${System.getProperty("java.vm.name")} ${System.getProperty("java.version")}")
        record("OS=${System.getProperty("os.name")} ${System.getProperty("os.arch")}; JCA=${Cipher.getInstance("AES/GCM/NoPadding").provider.name}")
        record("workload=bounded serialization + AES-256-GCM encrypt + authenticated read + full comparison; heap=256MiB")
        val allocations = (ManagementFactory.getThreadMXBean() as? ThreadMXBean)?.takeIf { it.isThreadAllocatedMemorySupported }
        allocations?.let { it.isThreadAllocatedMemoryEnabled = true }
        val thread = Thread.currentThread().threadId()
        val cipher = aesGcmCipher(ByteArray(32)) // Public all-zero benchmark key; never a production key.
        val serializer = BytesSerializer.encrypted(cipher, "benchmark")
        record("bytes,iterations,p50_us,p95_us,roundtrip_MiB_s,allocated_bytes_per_roundtrip")
        for ((size, warmup, iterations) in listOf(Triple(1024, 200, 500), Triple(65_536, 100, 200), Triple(1_048_576, 20, 50))) {
            val payload = ByteArray(size) { it.toByte() }
            suspend fun roundtrip() {
                val encoded = Buffer()
                serializer.writeTo(payload, encoded)
                check(encoded.size == size + 34L)
                check(serializer.readFrom(encoded).contentEquals(payload))
            }
            repeat(warmup) { roundtrip() }
            val durations = LongArray(iterations)
            val allocatedBefore = allocations?.getThreadAllocatedBytes(thread)
            val start = System.nanoTime()
            repeat(iterations) { index ->
                val before = System.nanoTime()
                roundtrip()
                durations[index] = System.nanoTime() - before
            }
            val elapsed = System.nanoTime() - start
            val allocatedAfter = allocations?.getThreadAllocatedBytes(thread)
            durations.sort()
            val p50 = durations[(iterations - 1) / 2] / 1000.0
            val p95 = durations[((iterations - 1) * 0.95).toInt()] / 1000.0
            val throughput = size.toDouble() * iterations / 1_048_576 / (elapsed / 1e9)
            val allocated = if (allocatedBefore != null && allocatedAfter != null) (allocatedAfter - allocatedBefore) / iterations else null
            record("$size,$iterations,$p50,$p95,$throughput,${allocated ?: "unavailable"}")
        }
        val reportPath = Path.of("build/reports/benchmarks/encrypted-datastore.csv")
        Files.createDirectories(reportPath.parent)
        Files.writeString(reportPath, report.joinToString("\n", postfix = "\n"))
        println("Benchmark report: ${reportPath.toAbsolutePath()}")
    }
}
