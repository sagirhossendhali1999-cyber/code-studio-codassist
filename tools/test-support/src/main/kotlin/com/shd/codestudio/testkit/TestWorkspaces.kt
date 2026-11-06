package com.shd.codestudio.testkit

import com.shd.codestudio.model.BuildSystemId
import com.shd.codestudio.model.FacetCodecRegistry
import com.shd.codestudio.model.FacetTemplate
import com.shd.codestudio.model.ModuleType
import com.shd.codestudio.model.ModuleTypeRegistry
import com.shd.codestudio.model.SourceSetTemplate
import com.shd.codestudio.model.impl.ProjectModel
import com.shd.codestudio.model.impl.ProjectModelStore
import com.shd.codestudio.model.impl.open
import com.shd.codestudio.platform.PluginId
import com.shd.codestudio.platform.impl.PlatformCore
import java.nio.file.Path

/** A minimal [ModuleType] stand-in for tests (real ones ship in the java/android support modules). */
class TestModuleType(override val id: String) : ModuleType {
    override val displayName: String get() = id
    override fun defaultSourceSets(): List<SourceSetTemplate> = emptyList()
    override fun defaultFacets(): List<FacetTemplate> = emptyList()
    override fun supportedBuildSystems(): Set<BuildSystemId> = setOf(BuildSystemId.NATIVE)
}

/**
 * Register test [ModuleType]s on this platform's extension registry. With no arguments registers the common
 * `java-lib` and `java-cli` types; otherwise registers a [TestModuleType] for each given id.
 */
fun PlatformCore.registerTestTypes(vararg ids: String) {
    val types = ModuleTypeRegistry(extensions)
    val names = if (ids.isEmpty()) arrayOf("java-lib", "java-cli") else ids
    for (id in names) types.register(TestModuleType(id), PluginId("java-support"))
}

/** Open a fresh [ProjectModelStore] rooted at [dir] with optional facet [codecs]. */
fun openWorkspace(
    dir: Path,
    platform: PlatformCore,
    codecs: FacetCodecRegistry = FacetCodecRegistry(),
): ProjectModelStore = ProjectModel.open(dir, platform, codecs)

/**
 * Open a workspace in a throwaway temp dir with test module types registered, run [block], then dispose the
 * platform and delete the dir. Replaces the per-module `withWorkspace` / `buildWorkspace` boilerplate; the
 * project/module graph is constructed inside [block] off the supplied [ProjectModelStore].
 */
inline fun <T> withWorkspace(
    prefix: String = "codestudio-ws",
    codecs: FacetCodecRegistry = FacetCodecRegistry(),
    block: (PlatformCore, ProjectModelStore) -> T,
): T = testEnv(prefix) { env ->
    env.platform.registerTestTypes()
    block(env.platform, openWorkspace(env.dir, env.platform, codecs))
}
