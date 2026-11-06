// Copyright (C) 2026 sagirhossendhali1999-cyber
// SPDX-License-Identifier: GPL-3.0-or-later WITH Classpath-exception-2.0
// See LICENSE-EXCEPTION: a plugin linking against this file may use any license.
package com.shd.codestudio.model.impl

import java.nio.file.Path
import java.nio.file.Paths
import com.shd.codestudio.model.impl.open

/**
 * The model's paths as `java.nio.file.Path`, for the JVM hosts.
 *
 * The model itself holds strings — that is what let it leave the JVM — but everything above it here does
 * not: the build engine hands `Path`s to a compiler, the Gradle importer walks a `Path`, JDT wants `File`s.
 * Converting at every one of those call sites would have been the actual cost of the port, and it would
 * have bought nothing: those callers are JVM-only by nature and a `Path` is the right type for them.
 *
 * So the conversion lives here, once. `store.rootPath` still answers a `Path`; `LocalFileSystem` still
 * takes one, and still hands out a `VirtualFile` for one (see `JvmLocalFileSystem.kt`, which is beside
 * the class so that importing the class is enough).
 */

/** The workspace root as a `Path`. [ProjectModelStore.root] is the same value as a string. */
val ProjectModelStore.rootPath: Path get() = Paths.get(root)

/** Open (loading from disk if present) or create an empty workspace at [workspaceRoot]. */
fun ProjectModel.open(
    workspaceRoot: Path,
    platform: com.shd.codestudio.platform.impl.PlatformCore,
    facetCodecs: com.shd.codestudio.model.FacetCodecRegistry = com.shd.codestudio.model.FacetCodecRegistry(),
    appContainer: com.shd.codestudio.platform.ServiceContainer = com.shd.codestudio.platform.impl.ApplicationContainer(),
): ProjectModelStore = open(workspaceRoot.toAbsolutePath().toString(), platform, facetCodecs, appContainer)
