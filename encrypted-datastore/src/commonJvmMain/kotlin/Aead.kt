package com.dayanruben.datastore.encrypted

import com.google.crypto.tink.Aead
import java.io.InputStream

internal fun Aead.newDecryptedStream(inputStream: InputStream): InputStream {
    // available() is only a nonblocking estimate, not an EOF check.
    val ciphertext = inputStream.readBytes()
    return if (ciphertext.isNotEmpty()) {
        decrypt(ciphertext, null).inputStream()
    } else {
        ciphertext.inputStream()
    }
}
