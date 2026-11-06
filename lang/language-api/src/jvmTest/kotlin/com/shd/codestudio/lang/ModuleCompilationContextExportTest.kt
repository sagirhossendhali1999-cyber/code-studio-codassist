package com.shd.codestudio.lang

import com.shd.codestudio.testkit.withTempDir
import com.shd.codestudio.model.BuildSystemId
import com.shd.codestudio.model.DependencyScope
import com.shd.codestudio.model.FacetTemplate
import com.shd.codestudio.model.LibraryDependency
import com.shd.codestudio.model.LibraryKind
import com.shd.codestudio.model.LibraryRef
import com.shd.codestudio.model.ModuleDependency
import com.shd.codestudio.model.ModuleId
import com.shd.codestudio.model.ModuleType
import com.shd.codestudio.model.SourceSetTemplate
import com.shd.codestudio.model.FacetCodecRegistry
import com.shd.codestudio.model.ModuleTypeRegistry
import com.shd.codestudio.model.impl.ProjectModel
import com.shd.codestudio.model.impl.ProjectModelStore
import com.shd.codestudio.platform.PluginId
import com.shd.codestudio.platform.impl.PlatformCore
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import com.shd.codestudio.model.impl.open
import com.shd.codestudio.vfs.local.fileFor
import com.shd.codestudio.model.impl.rootPath

/**
 * Regression: the **analysis/completion** classpath ([ModuleCompilationContext]) must honor `api` export
 * semantics across a module dependency. An exported (`api`) library of a dependency belongs on the
 * dependent module's classpath; an `implementation` library of a dependency does not.
 *
 * The model's own `ClasspathAssemblyTest` covers the *build* classpath ([com.shd.codestudio.model.Module.classpath]);
 * this guards the separate `collect()` walk that the editor's analyzer uses — the path where an `api`
 * library (e.g. Gson) added to a library module once failed to resolve in a dependent module's analysis
 * even though completion still surfaced it from the index.
 */
class ModuleCompilationContextExportTest {

    private class TestType(override val id: String) : ModuleType {
        override val displayName get() = id
        override fun defaultSourceSets(): List<SourceSetTemplate> = emptyList()
        override fun defaultFacets(): List<FacetTemplate> = emptyList()
        override fun supportedBuildSystems(): Set<BuildSystemId> = setOf(BuildSystemId.NATIVE)
    }

    private fun withWorkspace(block: (ProjectModelStore) -> Unit) = withTempDir("mcc-export") { dir ->
        val platform = PlatformCore()
        ModuleTypeRegistry(platform.extensions).register(TestType("java-lib"), PluginId("java-support"))
        try {
            block(ProjectModel.open(dir, platform, FacetCodecRegistry()))
        } finally {
            platform.dispose()
            dir.toFile().deleteRecursively()
        }
    }

    private fun ProjectModelStore.createJar(name: String) {
        workspace.libraryTable.create(name).apply {
            kind = LibraryKind.JAR
            addClassesRoot(vfs.fileFor(rootPath.resolve("libs/$name.jar")))
            commit()
        }
    }

    private fun ProjectModelStore.moduleNamed(name: String) =
        workspace.projects.single().modules.first { it.name == name }

    @Test
    fun exportedLibraryOfADependencyReachesTheDependentAnalysisClasspath() = withWorkspace { store ->
        // app --(module dep)--> lib ;  lib --api--> apiLib ,  lib --implementation--> implLib
        val javaLib = TestType("java-lib")
        store.createJar("apiLib")
        store.createJar("implLib")
        store.workspace.beginModification().apply { addProject("demo", BuildSystemId.NATIVE, store.vfs.root()); commit() }
        store.workspace.projects.single().beginModification().apply {
            addModule("lib", javaLib).apply {
                addDependency(LibraryDependency(LibraryRef("apiLib"), DependencyScope.API, exported = true))
                addDependency(LibraryDependency(LibraryRef("implLib"), DependencyScope.IMPLEMENTATION, exported = false))
            }
            addModule("app", javaLib).apply {
                addDependency(ModuleDependency(ModuleId("lib"), DependencyScope.API, exported = true))
            }
            commit()
        }

        val classpath = ModuleCompilationContext.create(store.workspace, store.moduleNamed("app")).classpath.entries
        val paths = classpath.map { it.root.path }
        assertTrue(paths.any { it.contains("apiLib.jar") }, "exported (api) library of a dependency must be on the dependent's analysis classpath: $paths")
        assertFalse(paths.any { it.contains("implLib.jar") }, "an implementation library of a dependency must NOT leak to the dependent: $paths")
    }

    @Test
    fun aModulesOwnDirectLibraryIsAlwaysOnItsClasspath() = withWorkspace { store ->
        // Even a non-exported (implementation) library declared directly on the module is on its own classpath.
        store.createJar("directLib")
        store.workspace.beginModification().apply { addProject("demo", BuildSystemId.NATIVE, store.vfs.root()); commit() }
        store.workspace.projects.single().beginModification().apply {
            addModule("app", TestType("java-lib")).apply {
                addDependency(LibraryDependency(LibraryRef("directLib"), DependencyScope.IMPLEMENTATION, exported = false))
            }
            commit()
        }

        val paths = ModuleCompilationContext.create(store.workspace, store.moduleNamed("app")).classpath.entries.map { it.root.path }
        assertTrue(paths.any { it.contains("directLib.jar") }, "a module's own direct library must be on its classpath: $paths")
    }
}
