package com.shd.codestudio.core.plugins

import com.shd.codestudio.model.ContentRole
import com.shd.codestudio.model.DependencyScope
import com.shd.codestudio.model.Module
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * What makes a module a Code Studio plugin, as far as the IDE is concerned: it packages a
 * `res/raw/codestudio_plugin.toml`. That file is the same thing the package manager hands another install of
 * the IDE at discovery time, so it is the one marker that cannot drift from reality.
 *
 * Deliberately not a facet. A facet would be a second declaration of the same fact, recorded in
 * `module.toml`, free to disagree with whether the manifest is actually there, and absent from any plugin
 * project the user did not create from the template. Detecting the file costs a directory probe and is
 * always right.
 */
object PluginProject {

    /** The plugin manifest this module packages, or null if it packages none. */
    fun manifestIn(module: Module): Path? =
        resRoots(module).map { it.resolve(RAW).resolve(MANIFEST_NAME) }.firstOrNull { Files.exists(it) }

    /** True when [module] builds a Code Studio plugin. */
    fun isPluginModule(module: Module): Boolean = manifestIn(module) != null

    /**
     * The Android manifest that belongs with [file], a resource under one of [module]'s res roots. Derived
     * from the module's declared roots rather than assumed, and null for a layout that does not place the
     * two in the conventional relationship, so a caller can skip rather than guess.
     */
    fun androidManifestBeside(module: Module, file: Path): Path? {
        val owning = resRoots(module).firstOrNull { file.startsWith(it) } ?: return null
        return owning.parent?.resolve(ANDROID_MANIFEST)?.takeIf { Files.exists(it) }
    }

    /** The module's non-test Android resource roots. */
    private fun resRoots(module: Module): List<Path> =
        module.sourceSets
            .filter { it.scope != DependencyScope.TEST_IMPLEMENTATION }
            .flatMap { it.contentRoots }
            .filter { ContentRole.ANDROID_RES in it.roles }
            .map { Paths.get(it.dir.path) }

    /** The file name the IDE's discovery reads, and the only name these checks apply to. */
    const val MANIFEST_NAME = "codestudio_plugin.toml"

    /** The intent action a plugin app's marker activity declares. */
    const val PLUGIN_ACTION = "com.shd.codestudio.codestudio.action.PLUGIN"

    /** The meta-data key pointing at the packaged manifest resource. */
    const val META_MANIFEST = "com.shd.codestudio.codestudio.plugin.manifest"

    private const val RAW = "raw"
    private const val ANDROID_MANIFEST = "AndroidManifest.xml"
}
