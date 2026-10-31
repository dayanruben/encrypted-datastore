package com.dayanruben.datastore.encrypted.kmp

import okio.BufferedSource

/**
 * Serializes immutable settings through a bounded sequential writer.
 *
 * The adapter checks each write before copying into its plaintext buffer. Allocations made privately
 * by this serializer, including encoding its input, remain the serializer's responsibility.
 */
public interface RecordSerializer<T> {
    public val defaultValue: T
    public suspend fun readFrom(source: BufferedSource): T
    public suspend fun writeTo(t: T, writer: RecordWriter)
}
