// Copyright (C) 2026 sagirhossendhali1999-cyber
// SPDX-License-Identifier: GPL-3.0-or-later WITH Classpath-exception-2.0
// See LICENSE-EXCEPTION: a plugin linking against this file may use any license.
package com.shd.codestudio.deps.impl

import java.nio.file.Path

/**
 * [ResolverCache] rooted at a `java.nio.file.Path`.
 *
 * Same shape as `LocalFileSystem(Path)`: the class lives in `commonMain` and holds its root as a string,
 * so a constructor cannot be added to it from here, and a function named like the class keeps every JVM
 * call site compiling on the import it already has.
 */
@Suppress("FunctionName")
fun ResolverCache(root: Path): ResolverCache = ResolverCache(root.toString())
