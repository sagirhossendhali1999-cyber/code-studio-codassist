package com.shd.codestudio.model.impl

import com.shd.codestudio.model.BuildSystemId
import com.shd.codestudio.model.ContentRole
import com.shd.codestudio.model.DependencyScope
import com.shd.codestudio.model.FacetCodecRegistry
import com.shd.codestudio.model.LibraryDependency
import com.shd.codestudio.model.LibraryRef
import com.shd.codestudio.model.ModuleDependency
import com.shd.codestudio.model.ModuleId
import com.shd.codestudio.model.ModuleTypeRegistry
import com.shd.codestudio.model.SdkDependency
import com.shd.codestudio.model.SdkRef
import com.shd.codestudio.model.SourceSetTemplate
import com.shd.codestudio.platform.impl.PlatformCore
import com.shd.codestudio.testkit.withTempDir
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContains
import com.shd.codestudio.model.impl.open

/**
 * Pins the human-readable on-disk shape: a declarative `module.toml` manifest and a `workspace.json`.
 * Acts as a regression guard on the serialized format, separate from the structural round-trip in
 * [PersistenceRoundTripTest].
 */
class OnDiskFormatTest {

    @Test
    fun writesReadableWorkspaceJsonAndModuleToml() = withTempDir("codestudio-format") { dir ->
        val platform = PlatformCore()
        platform.registerTestTypes()
        try {
            val store = ProjectModel.open(dir, platform, FacetCodecRegistry().register(JavaFacetCodec))
            val javaLib = ModuleTypeRegistry(platform.extensions).resolve("java-lib")

            store.workspace.beginModification().apply {
                addProject("app", BuildSystemId.NATIVE, store.vfs.root())
                commit()
            }
            store.workspace.projects.single().beginModification().apply {
                addModule("core", javaLib).apply {
                    addSourceSet(SourceSetTemplate("main", DependencyScope.IMPLEMENTATION, mapOf("src/main/java" to setOf(ContentRole.SOURCE))))
                    addDependency(ModuleDependency(ModuleId("shared"), DependencyScope.API, exported = true))
                    addDependency(LibraryDependency(LibraryRef("g:a:1"), DependencyScope.IMPLEMENTATION))
                    addDependency(SdkDependency(SdkRef("jdk-17"), DependencyScope.COMPILE_ONLY))
                    putFacet(JavaFacet(listOf("dagger.X"), preview = false))
                }
                commit()
            }
            store.save()

            val workspaceJson = Files.readString(dir.resolve(".platform/workspace.json"))
            assertContains(workspaceJson, "\"version\": 1")
            assertContains(workspaceJson, "\"buildSystem\": \"native\"")
            assertContains(workspaceJson, "\"id\": \"core\"")

            val moduleToml = Files.readString(dir.resolve("core/module.toml"))
            assertContains(moduleToml, "version = 2")
            assertContains(moduleToml, "[module]")
            assertContains(moduleToml, "type = \"java-lib\"")
            assertContains(moduleToml, "[sourceSets.main]")
            assertContains(moduleToml, "scope = \"IMPLEMENTATION\"")
            assertContains(moduleToml, "java = [\"src/main/java\"]")
            assertContains(moduleToml, "[dependencies]")
            assertContains(moduleToml, "api = [{ module = \"shared\" }]")
            assertContains(moduleToml, "implementation = [\"g:a:1\"]")
            assertContains(moduleToml, "compileOnly = [{ sdk = \"jdk-17\" }]")
            assertContains(moduleToml, "[java]")
            assertContains(moduleToml, "preview = false")
        } finally {
            platform.dispose()
            dir.toFile().deleteRecursively()
        }
    }

    /** A variant-qualified dependency lands under a nested `[dependencies.<config>]` table; the bare
     *  `[dependencies]` table keeps only the shared (unqualified) entries, byte-identical to the old format. */
    @Test
    fun variantQualifiedDependenciesUseNestedTables() = withTempDir("codestudio-variant-format") { dir ->
        val platform = PlatformCore()
        platform.registerTestTypes()
        try {
            val store = ProjectModel.open(dir, platform, FacetCodecRegistry().register(JavaFacetCodec))
            val javaLib = ModuleTypeRegistry(platform.extensions).resolve("java-lib")
            store.workspace.beginModification().apply {
                addProject("app", BuildSystemId.NATIVE, store.vfs.root()); commit()
            }
            store.workspace.projects.single().beginModification().apply {
                addModule("core", javaLib).apply {
                    addSourceSet(SourceSetTemplate("main", DependencyScope.IMPLEMENTATION, mapOf("src/main/java" to setOf(ContentRole.SOURCE))))
                    addDependency(LibraryDependency(LibraryRef("g:shared:1"), DependencyScope.IMPLEMENTATION))
                    addDependency(LibraryDependency(LibraryRef("g:debugonly:1"), DependencyScope.IMPLEMENTATION, variant = "debug"))
                }
                commit()
            }
            store.save()

            val moduleToml = Files.readString(dir.resolve("core/module.toml"))
            assertContains(moduleToml, "[dependencies]")
            assertContains(moduleToml, "implementation = [\"g:shared:1\"]")
            assertContains(moduleToml, "[dependencies.debug]")
            assertContains(moduleToml, "implementation = [\"g:debugonly:1\"]")
        } finally {
            platform.dispose()
            dir.toFile().deleteRecursively()
        }
    }
}
