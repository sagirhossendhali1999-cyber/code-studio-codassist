package com.shd.codestudio.index.impl

import platform.Foundation.NSTemporaryDirectory

internal actual fun scratchPath(name: String): String = NSTemporaryDirectory() + "index-impl-" + name
