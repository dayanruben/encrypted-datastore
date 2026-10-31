package com.dayanruben.datastore.encrypted.kmp

import okio.IOException

/** Encryption failures deliberately do not trigger DataStore corruption replacement. */
public open class EncryptionException(message: String, cause: Throwable? = null) : IOException(message, cause)

/** The ciphertext could not be authenticated. No unauthenticated plaintext is returned. */
public class AuthenticationException(cause: Throwable? = null) :
    EncryptionException("DataStore ciphertext authentication failed", cause)

/** The file is truncated or does not have a supported encrypted record header. */
public class UnsupportedFormatException : EncryptionException("Unsupported encrypted DataStore format")

/** The configured record limit was exceeded; the value was not written. */
public class PayloadTooLargeException(public val maxPlaintextBytes: Int) :
    EncryptionException("Encrypted DataStore exceeds its configured byte limit")

/** A platform key could not be created or opened. [status] is an OS status when available. */
public class KeyStorageException(message: String, public val status: Int? = null, cause: Throwable? = null) :
    EncryptionException(message, cause)
