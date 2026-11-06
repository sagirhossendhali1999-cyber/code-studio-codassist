package com.shd.codestudio.kotlin.classfile

actual fun readEnv(name: String): String? = System.getenv(name)
