@file:OptIn(ExperimentalForeignApi::class)

package com.shd.codestudio.kotlin.classfile

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import platform.posix.getenv

actual fun readEnv(name: String): String? = getenv(name)?.toKString()
