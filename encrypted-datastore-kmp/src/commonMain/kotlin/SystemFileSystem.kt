package com.dayanruben.datastore.encrypted.kmp

import okio.FileSystem

// Okio exposes SYSTEM on file-capable platforms, not in its common metadata API.
internal expect fun systemFileSystem(): FileSystem
