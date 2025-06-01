package com.dayanruben.security.crypto.datastore

/**
 * Represents a platform-specific context.
 * On Android, this will be `android.content.Context`.
 * On iOS, this might be unused or could provide access to application-specific paths or settings.
 * For other platforms, it can be a no-op or provide necessary environment details.
 */
expect class PlatformContext
