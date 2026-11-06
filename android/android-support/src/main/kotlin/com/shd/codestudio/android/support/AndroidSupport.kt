package com.shd.codestudio.android.support

import com.shd.codestudio.android.support.templates.AndroidAppTemplate
import com.shd.codestudio.android.support.templates.AndroidLibraryTemplate
import com.shd.codestudio.android.support.templates.Game2048SampleTemplate
import com.shd.codestudio.android.support.templates.JetpackComposeAppTemplate
import com.shd.codestudio.android.support.templates.MaterialYouAppTemplate
import com.shd.codestudio.android.support.templates.MemoryMatchSampleTemplate
import com.shd.codestudio.android.support.templates.SnakeSampleTemplate
import com.shd.codestudio.android.support.templates.TicTacToeSampleTemplate
import com.shd.codestudio.android.support.tools.SharedDexClasspath
import com.shd.codestudio.model.FacetCodecRegistry
import com.shd.codestudio.model.FileIconRegistry
import com.shd.codestudio.model.ModuleTypeRegistry
import com.shd.codestudio.model.ProjectTemplateRegistry
import com.shd.codestudio.platform.PluginId

/**
 * The android-support plugin entry point: contributes the Android module types and the [AndroidFacet]
 * codec to a host's registries. A host (`:ide-core`, `:ide-android`) calls this once at startup so
 * `module.toml` files of type `android-app`/`android-lib` load with a resolvable type and a decodable
 * facet, and the project-structure UI can create new Android modules.
 */
object AndroidSupport {
    val PLUGIN = PluginId("android-support")

    fun register(moduleTypes: ModuleTypeRegistry, codecs: FacetCodecRegistry) {
        moduleTypes.register(AndroidAppModuleType, PLUGIN)
        moduleTypes.register(AndroidLibModuleType, PLUGIN)
        codecs.register(AndroidFacetCodec, PLUGIN)
    }

    /** Contribute the Android tree icons (res/assets/manifest/android-module) to a host's icon registry. */
    fun registerIcons(icons: FileIconRegistry) {
        icons.register(AndroidFileIconProvider, PLUGIN)
    }

    /** Contribute the Android project templates (app, Material You app, library) to a host's Create-Project gallery. */
    fun registerTemplates(templates: ProjectTemplateRegistry) {
        templates.register(AndroidAppTemplate, PLUGIN)
        templates.register(MaterialYouAppTemplate, PLUGIN)
        templates.register(JetpackComposeAppTemplate, PLUGIN)
        templates.register(AndroidLibraryTemplate, PLUGIN)
    }

    /** Contribute the Jetpack Compose sample games (Snake, Tic-Tac-Toe, Memory Match, 2048) as sample projects. */
    fun registerComposeSamples(templates: ProjectTemplateRegistry) {
        templates.register(SnakeSampleTemplate, PLUGIN)
        templates.register(TicTacToeSampleTemplate, PLUGIN)
        templates.register(MemoryMatchSampleTemplate, PLUGIN)
        templates.register(Game2048SampleTemplate, PLUGIN)
    }

    /**
     * Release the process-wide dex caches held between builds — the shared, once-parsed D8 classpath providers
     * ([SharedDexClasspath]): open archive handles plus the retained class-descriptor indexes for `android.jar`
     * and every library jar. Standing heap + file descriptors that outlive a build and are otherwise held for
     * the whole session. A host wires this to the build (and preview) process's `onTrimMemory` so the OS's
     * memory-pressure signal reclaims them; they rebuild lazily on the next build, re-indexing `android.jar` once.
     *
     * Call ONLY when no build/render is dexing — closing a provider a running D8 invocation reads through would
     * break that dex. The caller is responsible for that idle check (e.g. `BuildDaemonService` gates on the
     * build/run being inactive).
     */
    fun releaseDexCaches() {
        SharedDexClasspath.clear()
    }
}
