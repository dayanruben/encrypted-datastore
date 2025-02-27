package com.dayanruben.datastore.encrypted

import com.google.crypto.tink.Aead
import java.io.InputStream

internal fun Aead.newDecryptedStream(inputStream: InputStream): InputStream {
    // Method 'decrypt' throws GeneralSecurityException for empty byte array,
    // so check it is not empty.
    return if (inputStream.available() > 0) {
        decrypt(inputStream.readBytes(), null).inputStream()
    } else {
        inputStream
    }
}
