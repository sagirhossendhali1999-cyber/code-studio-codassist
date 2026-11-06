package com.shd.codestudio.store.impl.platform

import com.shd.codestudio.platform.log.Log

internal actual fun storeLog(tag: String, message: String, error: Throwable?) {
    Log.logger(tag).warn(message, error)
}
