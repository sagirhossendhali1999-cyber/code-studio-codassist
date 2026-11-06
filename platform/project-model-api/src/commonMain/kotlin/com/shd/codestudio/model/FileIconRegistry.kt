package com.shd.codestudio.model

import com.shd.codestudio.platform.ExtensionRegistry
import com.shd.codestudio.platform.PluginId

/**
 * Resolves an [IconTarget] to an icon id against the [FileIconProvider]s plugins contributed to
 * [FileIconExtensionPoint]. Providers are tried highest-[priority][FileIconProvider.priority] first and
 * the first non-null answer wins; the host's built-in provider sits at priority 0 as an exhaustive
 * fallback, so [resolve] only returns null if no provider (not even that one) is registered.
 */
class FileIconRegistry(private val extensions: ExtensionRegistry) {
    fun register(provider: FileIconProvider, plugin: PluginId) =
        extensions.register(FileIconExtensionPoint, provider, plugin)

    fun resolve(target: IconTarget): String? =
        extensions.extensions(FileIconExtensionPoint)
            .sortedByDescending { it.priority }
            .firstNotNullOfOrNull { it.iconFor(target) }
}
