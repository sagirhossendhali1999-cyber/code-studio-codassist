package com.shd.codestudio.lang.kotlin

import com.shd.codestudio.platform.deleteFile
import com.shd.codestudio.platform.fileInfo
import com.shd.codestudio.platform.listDirectory

/** A per-run suffix, so two runs (or two tests) never share a scratch directory. */
internal expect fun nowSuffix(): String

/** Remove [path] and everything under it, best effort. */
internal fun deleteTree(path: String) {
    val info = fileInfo(path) ?: return
    if (info.isDirectory) for (child in listDirectory(path)) deleteTree(child)
    deleteFile(path)
}
