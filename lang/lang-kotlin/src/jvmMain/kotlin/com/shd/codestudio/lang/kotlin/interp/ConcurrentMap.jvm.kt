package com.shd.codestudio.lang.kotlin.interp

internal actual fun <K : Any, V : Any> concurrentMap(): MutableMap<K, V> = java.util.concurrent.ConcurrentHashMap()
