package com.dayanruben.datastore.encrypted.kmp

import okio.BufferedSource

internal object BytesSerializer : RecordSerializer<ByteArray> {
    override val defaultValue = byteArrayOf()
    override suspend fun readFrom(source: BufferedSource): ByteArray = source.readByteArray()
    override suspend fun writeTo(t: ByteArray, writer: RecordWriter) { writer.write(t) }
}

internal object Utf8Serializer : RecordSerializer<String> {
    override val defaultValue = ""
    override suspend fun readFrom(source: BufferedSource): String = source.readUtf8()
    override suspend fun writeTo(t: String, writer: RecordWriter) { writer.writeUtf8(t) }
}
