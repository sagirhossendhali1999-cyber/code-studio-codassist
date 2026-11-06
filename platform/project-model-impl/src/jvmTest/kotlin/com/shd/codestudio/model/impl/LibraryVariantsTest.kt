package com.shd.codestudio.model.impl

import com.shd.codestudio.model.BuildSystemId
import com.shd.codestudio.model.DependencyScope
import com.shd.codestudio.model.LibraryDependency
import com.shd.codestudio.model.LibraryKind
import com.shd.codestudio.model.LibraryRef
import com.shd.codestudio.model.LibraryVariants
import com.shd.codestudio.model.ModuleDependency
import com.shd.codestudio.model.ModuleId
import com.shd.codestudio.model.ModuleTypeRegistry
import com.shd.codestudio.vfs.local.fileFor
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Library entries are per module (see [LibraryVariants]). Two modules declaring one coordinate resolve it to
 * different artifacts (`ui-android` for an Android module, `ui-desktop` for a JVM one), and each must read its
 * own: the cross-read is how desktop jars reached an Android dex.
 */
class LibraryVariantsTest {

    private val coord = "org.jetbrains.compose.ui:ui:1.11.1"

    private fun ProjectModelStore.lib(name: String, vararg jars: String) = workspace.libraryTable.create(name).apply {
        kind = LibraryKind.JAR
        jars.forEach { addClassesRoot(vfs.fileFor(rootPath.resolve("libs/$it"))) }
        commit()
    }

    /** `shared` and `desktopApp` both declare [coord]; `app` depends on `shared`. */
    private fun ProjectModelStore.modules(platform: com.shd.codestudio.platform.impl.PlatformCore) {
        val javaLib = ModuleTypeRegistry(platform.extensions).resolve("java-lib")
        workspace.beginModification().apply { addProject("p", BuildSystemId.NATIVE, vfs.root()); commit() }
        workspace.projects.single().beginModification().apply {
            for (name in listOf("shared", "desktopApp")) addModule(name, javaLib).apply {
                addDependency(LibraryDependency(LibraryRef(coord), DependencyScope.IMPLEMENTATION))
            }
            commit()
        }
        val shared = workspace.projects.single().modules.first { it.name == "shared" }.id
        workspace.projects.single().beginModification().apply {
            addModule("app", javaLib).apply { addDependency(ModuleDependency(shared, DependencyScope.IMPLEMENTATION)) }
            commit()
        }
    }

    private fun ProjectModelStore.id(module: String) = workspace.projects.single().modules.first { it.name == module }.id.value

    private fun ProjectModelStore.jarsOf(module: String, scope: DependencyScope = DependencyScope.IMPLEMENTATION) =
        workspace.projects.single().modules.first { it.name == module }
            .classpath(scope).entries.map { it.root.path.substringAfterLast('/') }.filter { it.endsWith(".jar") }

    @Test
    fun eachModuleReadsItsOwnResolution() {
        withWorkspace { platform, store ->
            store.modules(platform)
            store.lib(LibraryVariants.scopedName(coord, store.id("shared")), "ui-android.jar")
            store.lib(LibraryVariants.scopedName(coord, store.id("desktopApp")), "ui-desktop.jar")
            assertEquals(listOf("ui-android.jar"), store.jarsOf("shared"))
            assertEquals(listOf("ui-desktop.jar"), store.jarsOf("desktopApp"))
        }
    }

    /** A module's runtime classpath reads a dependency module's library as THAT module resolved it. */
    @Test
    fun aDependencyModulesLibraryIsReadAsItResolvedIt() {
        withWorkspace { platform, store ->
            store.modules(platform)
            store.lib(LibraryVariants.scopedName(coord, store.id("shared")), "ui-android.jar")
            store.lib(LibraryVariants.scopedName(coord, store.id("desktopApp")), "ui-desktop.jar")
            assertEquals(listOf("ui-android.jar"), store.jarsOf("app", DependencyScope.RUNTIME_ONLY))
        }
    }

    /** An empty share still shadows a shared entry left from before entries were per module. */
    @Test
    fun anEmptyOwnEntryShadowsTheSharedOne() {
        withWorkspace { platform, store ->
            store.modules(platform)
            store.lib(coord, "ui-desktop.jar")
            store.lib(LibraryVariants.scopedName(coord, store.id("shared")))
            assertEquals(emptyList(), store.jarsOf("shared"))
        }
    }

    /** A project resolved before entries were per module has only plain entries, and keeps reading them. */
    @Test
    fun aProjectWithoutOwnEntriesReadsAsBefore() {
        withWorkspace { platform, store ->
            store.modules(platform)
            store.lib(coord, "ui-android.jar")
            assertEquals(listOf("ui-android.jar"), store.jarsOf("shared"))
            assertEquals(listOf("ui-android.jar"), store.jarsOf("desktopApp"))
        }
    }
}
