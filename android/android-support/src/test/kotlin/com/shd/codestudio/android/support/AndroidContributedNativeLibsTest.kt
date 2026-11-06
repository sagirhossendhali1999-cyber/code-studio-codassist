package com.shd.codestudio.android.support

import com.shd.codestudio.android.support.tools.AndroidSdk
import com.shd.codestudio.android.support.tools.SigningConfig
import com.shd.codestudio.build.BuildConfiguration
import com.shd.codestudio.build.BuildContext
import com.shd.codestudio.build.BuildGoal
import com.shd.codestudio.build.BuildPlugin
import com.shd.codestudio.build.BuildRequest
import com.shd.codestudio.build.Task
import com.shd.codestudio.build.TaskContext
import com.shd.codestudio.build.TaskGraph
import com.shd.codestudio.build.TaskInputs
import com.shd.codestudio.build.TaskInputsImpl
import com.shd.codestudio.build.TaskName
import com.shd.codestudio.build.TaskOutputs
import com.shd.codestudio.build.TaskOutputsImpl
import com.shd.codestudio.build.TaskResult
import com.shd.codestudio.build.VariantSelector
import com.shd.codestudio.build.engine.DefaultBuildEnv
import com.shd.codestudio.model.BuildSystemId
import com.shd.codestudio.model.FacetCodecRegistry
import com.shd.codestudio.model.FacetTemplate
import com.shd.codestudio.model.ModuleId
import com.shd.codestudio.model.ModuleType
import com.shd.codestudio.model.ModuleTypeRegistry
import com.shd.codestudio.model.Project
import com.shd.codestudio.model.SourceSetTemplate
import com.shd.codestudio.model.impl.ProjectModel
import com.shd.codestudio.model.impl.open
import com.shd.codestudio.platform.PluginId
import com.shd.codestudio.testkit.testEnv
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * A plugin that compiles native code declares its output through [BuildConfiguration.addNativeLibraries]
 * instead of writing into the user's `src/main/jniLibs`. The Android build has to package that directory and
 * order the packaging after the task that writes it, for an app and for a library's AAR alike, even though
 * the plugin is applied after every Android task has been registered.
 */
class AndroidContributedNativeLibsTest {

    private object JavaLib : ModuleType {
        override val id = "java-lib"
        override val displayName = "Java Library"
        override fun defaultSourceSets(): List<SourceSetTemplate> = emptyList()
        override fun defaultFacets(): List<FacetTemplate> = emptyList()
        override fun supportedBuildSystems(): Set<BuildSystemId> = setOf(BuildSystemId.NATIVE)
    }

    private class NoopTask(override val name: TaskName) : Task {
        override val inputs: TaskInputs get() = TaskInputsImpl()
        override val outputs: TaskOutputs get() = TaskOutputsImpl()
        override suspend fun execute(ctx: TaskContext): TaskResult = TaskResult.Success
    }

    /** Declares `<module>/build/native-out` for [moduleName], written by `:<module>:compileNative`. */
    private class NativePlugin(private val moduleName: String) : BuildPlugin {
        override val id = "native-test"
        lateinit var dir: Path

        override fun apply(config: BuildConfiguration) {
            val module = config.project.modules.single { it.name == moduleName }
            val task = TaskName(":$moduleName:compileNative")
            dir = config.env.buildDir(module).resolve("native-out")
            config.tasks.register(task) { NoopTask(task) }
            config.addNativeLibraries(module, dir, task)
        }
    }

    @Test
    fun appPackagesDeclaredNativeLibrariesAfterTheirProducer() = withProject { project, buildSystem, root ->
        val plugin = NativePlugin("app")
        val graph = graph(buildSystem, project, root, plugin, "app")

        val merge = graph.task(":app:mergeNativeLibsDebug")
        assertTrue(
            graph.dependencies(merge).any { it.name == TaskName(":app:compileNative") },
            "mergeNativeLibs must run after the task that writes the declared directory",
        )
        assertMergeReads(merge, plugin.dir)
    }

    @Test
    fun appPackagesNativeLibrariesDeclaredForAModuleItDependsOn() = withProject { project, buildSystem, root ->
        // `feature` is an android-lib the app depends on; its native output belongs in the app's APK too.
        val plugin = NativePlugin("feature")
        val graph = graph(buildSystem, project, root, plugin, "app")

        val merge = graph.task(":app:mergeNativeLibsDebug")
        assertTrue(graph.dependencies(merge).any { it.name == TaskName(":feature:compileNative") })
        assertMergeReads(merge, plugin.dir)
    }

    @Test
    fun libraryAarCarriesDeclaredNativeLibraries() = withProject { project, buildSystem, root ->
        val plugin = NativePlugin("feature")
        val graph = graph(buildSystem, project, root, plugin, "feature")

        val bundle = graph.task(":feature:bundleAar")
        assertTrue(graph.dependencies(bundle).any { it.name == TaskName(":feature:compileNative") })
        assertMergeReads(bundle, plugin.dir)
    }

    /** A library appearing in [dir] has to change [task]'s inputs, which is what proves the task reads it. */
    private fun assertMergeReads(task: Task, dir: Path) {
        val before = task.inputs.fingerprint()
        Files.createDirectories(dir.resolve("arm64-v8a"))
        Files.write(dir.resolve("arm64-v8a/libnative.so"), byteArrayOf(0x7f, 'E'.code.toByte(), 'L'.code.toByte(), 'F'.code.toByte()))
        assertNotEquals(before, task.inputs.fingerprint(), "${task.name} does not read ${dir.fileName}")
    }

    private fun TaskGraph.task(name: String): Task = tasks.single { it.name == TaskName(name) }

    private fun graph(
        buildSystem: AndroidBuildSystem,
        project: Project,
        root: Path,
        plugin: BuildPlugin,
        target: String,
    ): TaskGraph = buildSystem.createBuildGraph(
        project,
        BuildRequest(listOf(ModuleId(target)), VariantSelector("debug"), BuildGoal.PACKAGE),
        BuildContext(plugins = listOf(plugin), env = DefaultBuildEnv(root)),
    )

    private fun withProject(body: (Project, AndroidBuildSystem, Path) -> Unit) {
        testEnv("android-native-libs") { env ->
            val dir = env.dir
            val store = ProjectModel.open(dir, env.platform, FacetCodecRegistry().register(AndroidFacetCodec))
            val types = ModuleTypeRegistry(env.platform.extensions)
            types.register(JavaLib, PluginId("java-support"))
            AndroidSupport.register(types, FacetCodecRegistry())
            SampleAndroidProject.generate(
                store,
                androidApp = types.resolve("android-app"),
                androidLib = types.resolve("android-lib"),
                javaLib = types.resolve("java-lib"),
            )
            // Fake SDK / signing: graph construction only records paths, it never reads them.
            val sdk = AndroidSdk(androidJar = dir.resolve("fake/android.jar"), buildToolsDir = dir.resolve("fake/build-tools"))
            val signing = SigningConfig(dir.resolve("fake/debug.ks"), "android", "android", "android")
            val project = store.workspace.projects.single { it.name == SampleAndroidProject.PROJECT }
            body(project, AndroidBuildSystem.subprocess(sdk, signing), dir)
        }
    }
}
