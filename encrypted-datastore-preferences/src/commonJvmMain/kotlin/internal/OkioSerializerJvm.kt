package com.dayanruben.datastore.encrypted.internal

import androidx.annotation.RestrictTo
import androidx.datastore.core.Serializer
import androidx.datastore.core.okio.OkioSerializer
import okio.buffer
import okio.sink
import okio.source
import java.io.InputStream
import java.io.OutputStream

private class OkioToJvmSerializerAdapter<T>(
    private val delegate: OkioSerializer<T>,
) : Serializer<T> {

    override val defaultValue: T
        get() = delegate.defaultValue

    override suspend fun readFrom(input: InputStream): T {
        return input.source().buffer().use { delegate.readFrom(it) }
    }

    override suspend fun writeTo(t: T, output: OutputStream) {
        output.sink().buffer().use { delegate.writeTo(t, it) }
    }
}

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public fun <T> OkioSerializer<T>.asJvmSerializer(): Serializer<T> = OkioToJvmSerializerAdapter(this)
