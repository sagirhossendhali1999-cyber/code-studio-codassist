package com.shd.codestudio.core.kmp

import com.shd.codestudio.android.support.AndroidFacetCodec
import com.shd.codestudio.build.jvm.buildconfig.KotlinBuildConfigFacet
import com.shd.codestudio.build.jvm.buildconfig.KotlinBuildConfigFacetCodec
import com.shd.codestudio.build.jvm.compose.ComposeResourcesFacet
import com.shd.codestudio.build.jvm.compose.ComposeResourcesFacetCodec
import com.shd.codestudio.core.gradle.GradleImport
import com.shd.codestudio.lang.kotlin.build.KotlinFacetCodec
import com.shd.codestudio.model.ContentRole
import com.shd.codestudio.model.DependencyScope
import com.shd.codestudio.model.LanguageLevel
import com.shd.codestudio.model.impl.ExternalModelApplier
import com.shd.codestudio.model.impl.ModuleData
import com.shd.codestudio.model.impl.ProjectModelStore
import com.shd.codestudio.model.sync.ExternalFacet
import com.shd.codestudio.model.sync.ExternalLibrary
import com.shd.codestudio.model.sync.ExternalModule
import com.shd.codestudio.model.sync.ExternalProjectModel
import com.shd.codestudio.model.sync.ExternalSourceSet
import java.nio.file.Files
import java.nio.file.Path

/**
 * The features Module Settings can add to a native module, each a facet plus the roots and dependencies it
 * needs: Compose resources (`Res`), a Kotlin `BuildConfig` object, and Kotlin Multiplatform (an Android and a
 * desktop target, switchable). Applied through the importer's shape ([KmpSwitch.externalOf]) and the
 * [ExternalModelApplier], so a module gains a feature exactly as an imported one would carry it.
 */
internal object ModuleFeatures {

    const val COMPOSE_RESOURCES = "compose-resources"
    const val KOTLIN_BUILD_CONFIG = "kotlin-build-config"
    const val MULTIPLATFORM = "multiplatform"

    /** The features [module] does not have yet, by id. */
    fun addable(module: ModuleData): List<String> {
        val tables = module.facets.map { it.tomlTable }.toSet()
        val kotlin = module.facets.firstOrNull { it.tomlTable == KotlinFacetCodec.tomlTable }?.let { KotlinFacetCodec.decode(it.values) }
        val android = AndroidFacetCodec.tomlTable in tables
        return buildList {
            if (ComposeResourcesFacetCodec.tomlTable !in tables) add(COMPOSE_RESOURCES)
            // An Android-only module has AGP's BuildConfig; the Kotlin object is for JVM and multiplatform code.
            if (KotlinBuildConfigFacetCodec.tomlTable !in tables && (!android || kotlin?.multiplatform == true)) add(KOTLIN_BUILD_CONFIG)
            if (kotlin?.targets.isNullOrEmpty() && module.typeId in setOf("java-lib", "android-lib", "android-app")) add(MULTIPLATFORM)
        }
    }

    /** Add [feature] to the module [name]; the error to show, or null on success. */
    fun add(store: ProjectModelStore, root: Path, name: String, feature: String, level: LanguageLevel): String? {
        val project = store.data.projects.firstOrNull() ?: return "No project is open."
        val data = project.modules.firstOrNull { it.name == name } ?: return "No module '$name'."
        if (feature !in addable(data)) return "'$name' already has this, or cannot take it."
        val current = KmpSwitch.externalOf(project, data)
        val moduleDir = root.resolve(data.dirRelPath)
        val next = when (feature) {
            COMPOSE_RESOURCES -> composeResources(current, moduleDir)
            KOTLIN_BUILD_CONFIG -> kotlinBuildConfig(current)
            MULTIPLATFORM -> multiplatform(current, moduleDir)
            else -> return "Unknown feature '$feature'."
        }
        ExternalModelApplier(store).apply(
            ExternalProjectModel(project.name, com.shd.codestudio.model.BuildSystemId(project.buildSystemId), listOf(next)),
            level,
            removeAbsent = false,
            recreate = setOf(name),
        )
        store.save()
        return null
    }

    /** The package a generated class goes in: the Android namespace, else one made from the module's name. */
    private fun basePackage(module: ExternalModule): String =
        (module.facets.firstOrNull { it.table == AndroidFacetCodec.tomlTable }?.values?.get("namespace") as? String)
            ?.takeIf { it.isNotBlank() }
            ?: module.name.lowercase().map { if (it.isLetterOrDigit()) it else '_' }.joinToString("")
                .let { if (it.firstOrNull()?.isDigit() == true) "_$it" else it }

    private fun isMultiplatform(module: ExternalModule) =
        module.facets.firstOrNull { it.table == KotlinFacetCodec.tomlTable }?.let { KotlinFacetCodec.decode(it.values).multiplatform } == true

    /** Add [root] to the `[kotlin]` common roots of a multiplatform module, so `commonMain` code sees generated code. */
    private fun withCommonRoot(module: ExternalModule, root: String): ExternalModule {
        if (!isMultiplatform(module)) return module
        return module.copy(facets = module.facets.map { f ->
            if (f.table != KotlinFacetCodec.tomlTable) f
            else {
                val facet = KotlinFacetCodec.decode(f.values)
                f.copy(values = KotlinFacetCodec.encode(facet.copy(commonSourceRoots = (facet.commonSourceRoots + root).distinct())))
            }
        })
    }

    private fun composeResources(module: ExternalModule, moduleDir: Path): ExternalModule {
        val dir = if (isMultiplatform(module)) "src/commonMain/composeResources" else "src/main/composeResources"
        runCatching { Files.createDirectories(moduleDir.resolve(dir).resolve("drawable")) }
        val facet = ComposeResourcesFacet("${basePackage(module)}.generated.resources", roots = listOf(dir))
        val runtime = GradleImport.composeAccessorCoordinate("components.resources", KmpNative.COMPOSE_VERSION)!!
        val withFacet = module.copy(
            sourceSets = module.sourceSets + ExternalSourceSet(
                "composeResources", DependencyScope.IMPLEMENTATION,
                linkedMapOf(
                    ComposeResourcesFacet.GENERATED_KOTLIN_ROOT to setOf(ContentRole.SOURCE),
                    ComposeResourcesFacet.GENERATED_RESOURCES_ROOT to setOf(ContentRole.RESOURCE),
                ),
            ),
            dependencies = module.dependencies +
                (if (module.dependencies.any { it is ExternalLibrary && it.coordinate.startsWith(runtime.substringBeforeLast(':')) }) emptyList()
                else listOf(ExternalLibrary(runtime))),
            facets = module.facets + ExternalFacet(ComposeResourcesFacetCodec.tomlTable, ComposeResourcesFacetCodec.encode(facet)),
        )
        return withCommonRoot(withFacet, ComposeResourcesFacet.GENERATED_KOTLIN_ROOT)
    }

    private fun kotlinBuildConfig(module: ExternalModule): ExternalModule {
        val facet = KotlinBuildConfigFacet(basePackage(module))
        val withFacet = module.copy(
            sourceSets = module.sourceSets + ExternalSourceSet(
                "buildConfig", DependencyScope.IMPLEMENTATION,
                linkedMapOf(KotlinBuildConfigFacet.GENERATED_KOTLIN_ROOT to setOf(ContentRole.SOURCE)),
            ),
            facets = module.facets + ExternalFacet(KotlinBuildConfigFacetCodec.tomlTable, KotlinBuildConfigFacetCodec.encode(facet)),
        )
        return withCommonRoot(withFacet, KotlinBuildConfigFacet.GENERATED_KOTLIN_ROOT)
    }

    /**
     * An existing single-target module made multiplatform: `src/commonMain` becomes the shared code, and the
     * module's current code stays where it is as the target it already builds for (an Android module's roots and
     * `[android]` config are its Android target's, a JVM module's its desktop target's). The other target is laid
     * down beside it, empty.
     */
    private fun multiplatform(module: ExternalModule, moduleDir: Path): ExternalModule {
        val android = module.typeId.startsWith("android")
        val namespace = basePackage(module)
        val active = if (android) KmpShapes.ANDROID else KmpShapes.JVM
        val generated = KmpNative.module(
            KmpNative.Options(
                name = module.name, dirRelPath = module.dirRelPath, namespace = namespace,
                application = module.typeId == "android-app", compose = false, active = active,
            )
        )
        // Keep what the module already is: its own roots and dependencies stay (as the active target's code), and
        // an Android module keeps its own `[android]` config rather than the generated defaults.
        val ownMain = module.sourceSets.firstOrNull { it.name == "main" }?.roots.orEmpty()
        val sets = generated.sourceSets.map { s ->
            if (s.name == "main") s.copy(roots = LinkedHashMap(s.roots).apply { putAll(ownMain) }) else s
        } + module.sourceSets.filter { it.name != "main" && it.name != "test" }
        val kotlin = generated.facets.single { it.table == KotlinFacetCodec.tomlTable }.let { f ->
            val facet = KotlinFacetCodec.decode(f.values)
            val activeTarget = facet.targets.first { it.name == active }
            val keep = ownMain.map { (path, roles) -> com.shd.codestudio.lang.kotlin.build.KmpRoots.encode(path, roles.map { it.id }) }
            // An Android module's own dependencies are its Android target's (AppCompat has no desktop variant); a JVM
            // module's stay shared, since what a JVM module uses mostly works on Android too.
            val ownDeps = if (!android) emptyList() else module.dependencies
                .filterNot { it is ExternalLibrary && it.coordinate == "kotlin-stdlib" }.map(KmpShapes::encodeDependency)
            // The generated code of features the module already has (Res, the BuildConfig object) is common too.
            val generatedRoots = listOfNotNull(
                ComposeResourcesFacet.GENERATED_KOTLIN_ROOT.takeIf { module.facets.any { m -> m.table == ComposeResourcesFacetCodec.tomlTable } },
                KotlinBuildConfigFacet.GENERATED_KOTLIN_ROOT.takeIf { module.facets.any { m -> m.table == KotlinBuildConfigFacetCodec.tomlTable } },
            )
            f.copy(values = KotlinFacetCodec.encode(facet.copy(
                commonSourceRoots = (facet.commonSourceRoots + generatedRoots).distinct(),
                targets = facet.targets.map { t ->
                if (t.name == active) activeTarget.copy(
                    roots = (activeTarget.roots + keep).distinct(),
                    dependencies = (activeTarget.dependencies + ownDeps).distinct(),
                ) else t
            },
            )))
        }
        val facets = module.facets.filter { it.table != KotlinFacetCodec.tomlTable } +
            generated.facets.filter { it.table != KotlinFacetCodec.tomlTable && module.facets.none { m -> m.table == it.table } } + kotlin
        for (dir in listOf("src/commonMain/kotlin", "src/commonTest/kotlin", if (android) "src/androidMain/kotlin" else "src/desktopMain/kotlin")) {
            runCatching { Files.createDirectories(moduleDir.resolve(dir)) }
        }
        return generated.copy(
            typeId = module.typeId,
            languageLevel = module.languageLevel,
            outputRelPath = module.outputRelPath,
            sourceSets = sets,
            dependencies = module.dependencies + generated.dependencies.filter { g -> module.dependencies.none { it == g } },
            facets = facets,
        )
    }
}
