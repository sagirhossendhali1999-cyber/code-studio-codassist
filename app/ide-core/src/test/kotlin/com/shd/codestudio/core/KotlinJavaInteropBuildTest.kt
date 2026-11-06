package com.shd.codestudio.core

import com.shd.codestudio.build.BuildGoal
import com.shd.codestudio.build.BuildRequest
import com.shd.codestudio.build.VariantSelector
import com.shd.codestudio.build.engine.BuildCache
import com.shd.codestudio.build.engine.SimpleTaskContext
import com.shd.codestudio.build.engine.TaskExecutorImpl
import com.shd.codestudio.build.engine.jarPath
import com.shd.codestudio.build.jvm.JavaBuildSystem
import com.shd.codestudio.lang.kotlin.compile.IncrementalKotlinCompiler
import com.shd.codestudio.lang.kotlin.compile.KotlinJvmCompiler
import com.shd.codestudio.model.BuildSystemId
import com.shd.codestudio.model.ContentRole
import com.shd.codestudio.model.DependencyScope
import com.shd.codestudio.model.FacetTemplate
import com.shd.codestudio.model.LibraryDependency
import com.shd.codestudio.model.LibraryKind
import com.shd.codestudio.model.LibraryRef
import com.shd.codestudio.model.ModuleId
import com.shd.codestudio.model.ModuleType
import com.shd.codestudio.model.Project
import com.shd.codestudio.model.SourceSetTemplate
import com.shd.codestudio.model.FacetCodecRegistry
import com.shd.codestudio.model.ModuleTypeRegistry
import com.shd.codestudio.model.impl.ProjectModel
import com.shd.codestudio.model.impl.ProjectModelStore
import com.shd.codestudio.platform.PluginId
import com.shd.codestudio.platform.impl.PlatformCore
import com.shd.codestudio.testkit.TestJars
import com.shd.codestudio.testkit.testEnv
import com.shd.codestudio.testkit.writeSource
import com.shd.codestudio.vfs.local.fileFor
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Path
import java.util.jar.JarFile
import kotlin.test.Test
import kotlin.test.assertTrue
import com.shd.codestudio.model.impl.open

/**
 * End-to-end Kotlin/Java interop in the native build: one module
 * with mixed `.kt`/`.java` sources where the dependency runs both ways:
 *  - `Greeter.kt` (Kotlin) calls `JavaUtil.shout(...)` (same-module Java): Kotlin to Java
 *  - `Main.java` (Java) constructs `Greeter` and calls `greet(...)` (Kotlin): Java to Kotlin
 * so the build only succeeds if `compileKotlin` resolves the Java sources AND `compileJava` sees the Kotlin
 * output on its classpath. Asserts the jar carries all three classes, then runs it (real `java`) and
 * checks the program output, proving the interop links and executes against the Kotlin stdlib.
 */
class KotlinJavaInteropBuildTest {

    private class JavaLib : ModuleType {
        override val id = "java-lib"
        override val displayName = "Java Library"
        override fun defaultSourceSets(): List<SourceSetTemplate> = emptyList()
        override fun defaultFacets(): List<FacetTemplate> = emptyList()
        override fun supportedBuildSystems(): Set<BuildSystemId> = setOf(BuildSystemId.NATIVE)
    }

    private fun buildWorkspace(dir: Path, platform: PlatformCore): Pair<ProjectModelStore, Project> {
        ModuleTypeRegistry(platform.extensions).register(JavaLib(), PluginId("java-support"))
        val store = ProjectModel.open(dir, platform, FacetCodecRegistry())
        val javaLib = ModuleTypeRegistry(platform.extensions).resolve("java-lib")
        store.workspace.beginModification().apply { addProject("demo", BuildSystemId.NATIVE, store.vfs.root()); commit() }
        store.workspace.libraryTable.create("kotlin-stdlib")
            .apply { kind = LibraryKind.JAR; addClassesRoot(store.vfs.fileFor(TestJars.kotlinStdlib())); commit() }
        val mixed = SourceSetTemplate(
            "main", DependencyScope.IMPLEMENTATION,
            mapOf("src/main/java" to setOf(ContentRole.SOURCE), "src/main/kotlin" to setOf(ContentRole.SOURCE)),
        )
        store.workspace.projects.single().beginModification().apply {
            addModule("app", javaLib).apply {
                addSourceSet(mixed)
                addDependency(LibraryDependency(LibraryRef("kotlin-stdlib"), DependencyScope.IMPLEMENTATION))
            }
            commit()
        }
        dir.writeSource("app/src/main/java/com/example/JavaUtil.java", JAVA_UTIL)
        dir.writeSource("app/src/main/kotlin/com/example/Greeter.kt", GREETER)
        dir.writeSource("app/src/main/java/com/example/Main.java", MAIN)
        return store to store.workspace.projects.single()
    }

    @Test
    fun buildsAndRunsAMixedKotlinJavaModule() {
        testEnv("ktjava-interop") { env ->
            val dir = env.dir
            val platform = env.platform
            val (_, project) = buildWorkspace(dir, platform)
            // Desktop wiring, exactly as IdeServices builds it: empty boot classpath (ecj/K2 use the host
            // JRE) and the real incremental K2 compiler. compileJava/compileKotlin call ecj/K2 directly.
            val build = JavaBuildSystem(kotlin = IncrementalKotlinCompiler(KotlinJvmCompiler()))
            val graph = build.createBuildGraph(
                project, BuildRequest(listOf(ModuleId("app")), VariantSelector("main"), BuildGoal.PACKAGE),
            )
            val exec = TaskExecutorImpl(BuildCache(dir.resolve(".caches/build")))
            val log = StringBuilder()
            val outcome = runBlocking { exec.execute(graph, SimpleTaskContext(log = { log.appendLine(it) }), 2) }
            assertTrue(outcome.succeeded, "mixed build failed:\n$log")

            val ran = outcome.ranTasks.map { it.value }
            assertTrue(":app:compileKotlin" in ran, "compileKotlin must run: $ran")
            assertTrue(ran.indexOf(":app:compileKotlin") < ran.indexOf(":app:compileJava"),
                "compileKotlin must run before compileJava: $ran")

            val jar = jarPath(project.modules.single())
            val names = JarFile(jar.toFile()).use { jf -> jf.entries().toList().map { it.name } }
            assertTrue("com/example/Greeter.class" in names, "Kotlin class missing from jar: $names")
            assertTrue("com/example/JavaUtil.class" in names, "Java class missing from jar: $names")
            assertTrue("com/example/Main.class" in names, "Java entrypoint missing from jar: $names")

            // Run it (real java fork): proves Java to Kotlin to Java links and executes against the stdlib.
            val runtime = listOf(jar, TestJars.kotlinStdlib())
            assertTrue("HELLO, WORLD!" in runJava(runtime, "com.example.Main"), "wrong program output")

            // A clean rebuild does nothing: both compiles are up-to-date.
            val again = runBlocking { exec.execute(graph, SimpleTaskContext(), 2) }
            assertTrue(again.ranTasks.isEmpty(), "re-build must be up-to-date, ran=${again.ranTasks.map { it.value }}")
        }
    }

    private fun runJava(classpath: List<Path>, mainClass: String): String {
        val javaBin = Path.of(System.getProperty("java.home"), "bin", "java").toString()
        val proc = ProcessBuilder(javaBin, "-cp", classpath.joinToString(File.pathSeparator), mainClass)
            .redirectErrorStream(true).start()
        val text = proc.inputStream.bufferedReader().readText()
        proc.waitFor()
        return text.trim()
    }

    private companion object {
        val JAVA_UTIL = """
            package com.example;
            public final class JavaUtil {
                public static String shout(String s) { return s.toUpperCase(); }
            }
        """
        // Kotlin to Java: references the same-module Java class JavaUtil (resolved from its source by kotlinc).
        val GREETER = """
            package com.example
            class Greeter {
                fun greet(name: String): String = JavaUtil.shout("hello, ${'$'}name")
            }
        """
        // Java to Kotlin: references the Kotlin class Greeter (resolved from the compileKotlin output).
        val MAIN = """
            package com.example;
            public class Main {
                public static void main(String[] args) {
                    System.out.println(new Greeter().greet("world") + "!");
                }
            }
        """
    }
}
