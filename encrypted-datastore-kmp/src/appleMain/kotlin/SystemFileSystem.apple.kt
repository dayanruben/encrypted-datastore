package com.dayanruben.datastore.encrypted.kmp

import okio.FileSystem

internal actual fun systemFileSystem(): FileSystem = FileSystem.SYSTEM
