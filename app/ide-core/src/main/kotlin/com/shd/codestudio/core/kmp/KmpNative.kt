package com.shd.codestudio.core.kmp

import com.shd.codestudio.android.support.AndroidApiLevels
import com.shd.codestudio.android.support.AndroidFacet
import com.shd.codestudio.android.support.AndroidFacetCodec
import com.shd.codestudio.android.support.BuildFeatures
import com.shd.codestudio.build.jvm.compose.ComposeResourcesFacet
import com.shd.codestudio.build.jvm.compose.ComposeResourcesFacetCodec
import com.shd.codestudio.core.gradle.GradleImport
import com.shd.codestudio.lang.kotlin.build.KotlinFacet
import com.shd.codestudio.lang.kotlin.build.KotlinFacetCodec
import com.shd.codestudio.model.ContentRole
import com.shd.codestudio.model.DependencyScope
import com.shd.codestudio.model.FacetData
import com.shd.codestudio.model.LanguageLevel
import com.shd.codestudio.model.LibraryDependency
import com.shd.codestudio.model.LibraryRef
import com.shd.codestudio.model.ModifiableModule
import com.shd.codestudio.model.ModuleDependency
import com.shd.codestudio.model.ModuleId
import com.shd.codestudio.model.PlatformDependency
import com.shd.codestudio.model.SourceSetTemplate
import com.shd.codestudio.model.sync.ExternalFacet
import com.shd.codestudio.model.sync.ExternalLibrary
import com.shd.codestudio.model.sync.ExternalModule
import com.shd.codestudio.model.sync.ExternalModuleRef
import com.shd.codestudio.model.sync.ExternalPlatform
import com.shd.codestudio.model.sync.ExternalSourceSet

/**
 * A Kotlin Multiplatform module authored natively (the template with Gradle turned off, New Module, Module
 * Settings' Add feature): Android and the desktop JVM, in the layout the KMP wizard uses (`src/commonMain`,
 * `src/androidMain`, `src/desktopMain`), recorded with both targets ([KmpShapes.describe]) and shaped for
 * [Options.active].
 */
object KmpNative {

    data class Options(
        val name: String,
        val dirRelPath: String = name,
        /** The Android target's namespace (and its `applicationId` for an app). */
        val namespace: String,
        /** An application (an APK and a desktop window) rather than a library. */
        val application: Boolean,
        /** Depend on Compose Multiplatform (runtime, foundation, Material 3, UI). */
        val compose: Boolean = true,
        /** Add `src/commonMain/composeResources` and the `Res` generator. */
        val composeResources: Boolean = false,
        /** The target to shape the module for now: [KmpShapes.ANDROID] or [KmpShapes.JVM]. */
        val active: String,
        val composeVersion: String = COMPOSE_VERSION,
        /** The JVM target's source-set directory: `desktopMain` for an app (the wizard's), `jvmMain` for a library. */
        val jvmSourceSet: String = "desktopMain",
    )

    /** The Compose Multiplatform the IDE ships and its KMP template declares (a desktop Run uses the IDE's copy). */
    const val COMPOSE_VERSION = "1.11.1"

    /** The module, shaped for [Options.active] with both targets recorded. */
    fun module(o: Options): ExternalModule = KmpShapes.describe(
        listOf(
            KmpShapes.Shaped(KmpShapes.ANDROID, KmpShapes.ANDROID, shape(o, android = true)),
            KmpShapes.Shaped(KmpShapes.JVM, KmpShapes.JVM, shape(o, android = false)),
        ),
        o.active,
    )

    private fun shape(o: Options, android: Boolean): ExternalModule {
        val platformSet = if (android) "androidMain" else o.jvmSourceSet
        val main = linkedMapOf<String, Set<ContentRole>>(
            "src/commonMain/kotlin" to setOf(ContentRole.SOURCE),
            "src/$platformSet/kotlin" to setOf(ContentRole.SOURCE),
        )
        if (android) main["src/androidMain/res"] = setOf(ContentRole.ANDROID_RES)
        else main["src/desktopMain/resources"] = setOf(ContentRole.RESOURCE)
        val sets = buildList {
            add(ExternalSourceSet("main", DependencyScope.IMPLEMENTATION, main))
            add(ExternalSourceSet("test", DependencyScope.TEST_IMPLEMENTATION, linkedMapOf("src/commonTest/kotlin" to setOf(ContentRole.SOURCE))))
            if (o.composeResources) add(
                ExternalSourceSet(
                    "composeResources", DependencyScope.IMPLEMENTATION,
                    linkedMapOf(
                        ComposeResourcesFacet.GENERATED_KOTLIN_ROOT to setOf(ContentRole.SOURCE),
                        ComposeResourcesFacet.GENERATED_RESOURCES_ROOT to setOf(ContentRole.RESOURCE),
                    ),
                )
            )
        }
        fun compose(path: String) = GradleImport.composeAccessorCoordinate(path, o.composeVersion)!!
        val deps = buildList {
            if (o.compose) {
                listOf("runtime", "foundation", "material3", "ui", "components.uiToolingPreview").forEach { add(ExternalLibrary(compose(it))) }
                if (o.composeResources) add(ExternalLibrary(compose("components.resources")))
            }
            add(ExternalLibrary("org.jetbrains.kotlin:kotlin-test:${KOTLIN_TEST_VERSION}", DependencyScope.TEST_IMPLEMENTATION))
            if (android && o.application && o.compose) add(ExternalLibrary("androidx.activity:activity-compose:1.13.0"))
            if (!android && o.compose) {
                add(ExternalLibrary(compose("desktop.currentOs")))
                add(ExternalLibrary("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.11.0"))
            }
        }
        val facets = buildList {
            if (android) add(
                ExternalFacet(
                    AndroidFacetCodec.tomlTable,
                    AndroidFacetCodec.encode(
                        AndroidFacet(
                            namespace = o.namespace,
                            compileSdk = AndroidApiLevels.LATEST,
                            minSdk = 24,
                            targetSdk = AndroidApiLevels.LATEST,
                            manifest = "src/androidMain/AndroidManifest.xml",
                            isApplication = o.application,
                            buildFeatures = BuildFeatures(compose = o.compose),
                        )
                    ),
                )
            )
            if (o.composeResources) add(
                ExternalFacet(
                    ComposeResourcesFacetCodec.tomlTable,
                    ComposeResourcesFacetCodec.encode(
                        ComposeResourcesFacet(
                            "${o.namespace}.generated.resources",
                            roots = listOf("src/commonMain/composeResources"),
                        )
                    ),
                )
            )
            val common = listOf("src/commonMain/kotlin") +
                (if (o.composeResources) listOf(ComposeResourcesFacet.GENERATED_KOTLIN_ROOT) else emptyList())
            add(ExternalFacet(KotlinFacetCodec.tomlTable, KotlinFacetCodec.encode(KotlinFacet(commonSourceRoots = common))))
        }
        return ExternalModule(
            name = o.name,
            dirRelPath = o.dirRelPath,
            typeId = when {
                !android -> "java-lib"
                o.application -> "android-app"
                else -> "android-lib"
            },
            sourceSets = sets,
            dependencies = deps,
            facets = facets,
        )
    }

    private const val KOTLIN_TEST_VERSION = "2.4.0"

    /**
     * Apply [module] to a module being built in a transaction ([target]), through the public model API: what
     * `ExternalModelApplier` does for an importer, for callers that hold a transaction rather than the store (a
     * template). [moduleIds] resolves a module dependency's name to its id (ids are names).
     */
    fun applyTo(target: ModifiableModule, module: ExternalModule, languageLevel: LanguageLevel) {
        target.dirRelPath = module.dirRelPath
        target.languageLevel = module.languageLevel ?: languageLevel
        for (set in module.sourceSets) target.addSourceSet(SourceSetTemplate(set.name, set.scope, set.roots))
        for (dep in module.dependencies) when (dep) {
            is ExternalLibrary -> target.addDependency(
                LibraryDependency(LibraryRef(dep.coordinate), dep.scope, exported = dep.scope == DependencyScope.API, exclusions = dep.exclusions, variant = dep.variant)
            )
            is ExternalModuleRef -> target.addDependency(
                ModuleDependency(ModuleId(dep.moduleName), dep.scope, exported = dep.scope == DependencyScope.API, variant = dep.variant)
            )
            is ExternalPlatform -> target.addDependency(PlatformDependency(dep.bom, dep.scope, variant = dep.variant))
        }
        for (f in module.facets) target.putFacetData(FacetData(f.table, f.values))
    }
}
