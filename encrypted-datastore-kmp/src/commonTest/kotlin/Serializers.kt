package com.dayanruben.datastore.encrypted.kmp

import androidx.datastore.core.okio.OkioSerializer
import okio.BufferedSink
import okio.BufferedSource

internal object BytesSerializer : OkioSerializer<ByteArray> {
    override val defaultValue = byteArrayOf()
    override suspend fun readFrom(source: BufferedSource): ByteArray = source.readByteArray()
    override suspend fun writeTo(t: ByteArray, sink: BufferedSink) { sink.write(t) }
}

internal object Utf8Serializer : OkioSerializer<String> {
    override val defaultValue = ""
    override suspend fun readFrom(source: BufferedSource): String = source.readUtf8()
    override suspend fun writeTo(t: String, sink: BufferedSink) { sink.writeUtf8(t) }
}
