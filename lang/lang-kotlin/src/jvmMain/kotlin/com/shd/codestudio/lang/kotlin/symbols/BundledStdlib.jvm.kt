package com.shd.codestudio.lang.kotlin.symbols

import com.shd.codestudio.lang.kotlin.compile.BundledKotlinStdlib

/** The jar `processResources` copies in at the `kotlin` version of `libs.versions.toml`. */
internal actual fun bundledStdlibJarPath(): String? = BundledKotlinStdlib.jar()?.toString()
