package com.dayanruben.datastore.encrypted

// Using expect typealias for platform streams
expect class PlatformInputStream {
    fun close() // Adding close method here
}
expect class PlatformOutputStream {
    fun close() // Adding close method here
}

// Common helper functions for streams might need to be re-evaluated
// or expect/actual functions for specific stream operations.
// For example, reading all bytes from a PlatformInputStream:
expect fun PlatformInputStream.platformReadBytes(): ByteArray

// Creating a PlatformInputStream from ByteArray:
expect fun ByteArray.toPlatformInputStream(): PlatformInputStream

// The kotlinx-io library provides common abstractions for Source and Sink
// which might be a better long-term solution than custom expect/actual streams.
// import kotlinx.io.Source
// import kotlinx.io.Sink
// expect interface PlatformSource : Source
// expect interface PlatformSink : Sink
