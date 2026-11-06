package com.shd.codestudio.lang.kotlin.build

import com.shd.codestudio.build.KotlinCompilerPlugin
import com.shd.codestudio.build.Task
import com.shd.codestudio.build.TaskContext
import com.shd.codestudio.build.TaskInputs
import com.shd.codestudio.build.TaskInputsImpl
import com.shd.codestudio.build.TaskName
import com.shd.codestudio.build.TaskOutputs
import com.shd.codestudio.build.TaskOutputsImpl
import com.shd.codestudio.build.TaskResult
import com.shd.codestudio.build.engine.depOutputDirs
import com.shd.codestudio.build.engine.info
import com.shd.codestudio.build.engine.kotlinOutputDir
import com.shd.codestudio.build.engine.kotlinSiblings
import com.shd.codestudio.build.engine.kotlinSourceFiles
import com.shd.codestudio.build.engine.levelOf
import com.shd.codestudio.build.engine.libJars
import com.shd.codestudio.build.engine.moduleDir
import com.shd.codestudio.build.engine.sourceFiles
import com.shd.codestudio.build.engine.withHeartbeat
import com.shd.codestudio.build.resolveFor
import com.shd.codestudio.lang.kotlin.compile.BUILTIN_KOTLIN_COMPILER_PLUGINS
import com.shd.codestudio.lang.kotlin.compile.IncrementalKotlinCompiler
import com.shd.codestudio.model.Module
import java.nio.file.Files
import java.nio.file.Path

/**
 * The `compileKotlin` build task for the Kotlin backend: K2 codegen of a module's `.kt` (with its `.java`
 * as resolution-only inputs) into the module's `kotlin-classes` dir, run ahead of `compileJava`, which then
 * puts that dir on its classpath (the Kotlin -> Java and Java -> Kotlin interop loop).
 *
 * It lives in lang-kotlin, next to [IncrementalKotlinCompiler], and drives it directly: the build graph no
 * longer routes Kotlin compilation through an injected port. The Compose compiler plugin is applied
 * automatically when the module depends on the Compose runtime (otherwise `@Composable` functions emit
 * unusable bytecode). [compiler] is the workspace's incremental compiler (it wraps the warm, app-scoped
 * `KotlinJvmCompiler`); [bootClasspath] (host JDK on the desktop, `android.jar` on ART) is host-specific and
 * passed in. The Java sources are declared inputs so a Java edit re-runs the Kotlin compile (interop shape).
 */
class KotlinCompileTask(
    private val module: Module,
    override val name: TaskName,
    private val bootClasspath: List<Path>,
    private val compiler: IncrementalKotlinCompiler,
    /** Kotlin compiler plugins to apply (those whose `appliesTo` matches the module). Defaults to the
     *  built-ins (Compose); the host passes the `platform.kotlinCompilerPlugin` EP contents. */
    private val plugins: List<KotlinCompilerPlugin> = BUILTIN_KOTLIN_COMPILER_PLUGINS,
) : Task {
    /**
     * The module's COMMON-fragment sources: every `.kt` under a root the `[kotlin]` facet names. An imported
     * KMP module compiles its common + platform sources as one compilation, and this is what tells the
     * compiler which half is which (see [KotlinFacet.commonSourceRoots]).
     */
    private fun commonSources(): List<Path> {
        val roots = module.facets.get(KotlinFacet.KEY)?.commonSourceRoots.orEmpty()
        if (roots.isEmpty()) return emptyList()
        val dirs = roots.map { moduleDir(module).resolve(it).toAbsolutePath().normalize() }
        return kotlinSourceFiles(module).filter { file ->
            val path = file.toAbsolutePath().normalize()
            dirs.any { path.startsWith(it) }
        }
    }

    private fun optIns(): List<String> = module.facets.get(KotlinFacet.KEY)?.optIns.orEmpty()

    private fun upstreamKotlin(): List<Path> = kotlinSiblings(depOutputDirs(module)).filter { Files.isDirectory(it) }
    private fun classpath(): List<Path> = depOutputDirs(module) + upstreamKotlin() + libJars(module)

    override val inputs: TaskInputs
        get() = TaskInputsImpl().apply {
            filePaths("kotlinSources", kotlinSourceFiles(module))
            filePaths("javaSources", sourceFiles(module))      // interop: a Java edit can change what Kotlin resolves
            dirPaths("deps", depOutputDirs(module) + upstreamKotlin())
            filePaths("libs", libJars(module))
            property("level", levelOf(module.languageLevel))
            // Which sources are common changes what compiles, so it is an input like the language level.
            filePaths("commonSources", commonSources())
            property("optIns", optIns().joinToString(","))
        }
    override val outputs: TaskOutputs
        get() = TaskOutputsImpl().apply { dirPath("classes", kotlinOutputDir(module)) }

    override suspend fun execute(ctx: TaskContext): TaskResult {
        ctx.checkCanceled()
        val out = kotlinOutputDir(module)
        Files.createDirectories(out)
        val kt = kotlinSourceFiles(module)
        if (kt.isEmpty()) return TaskResult.Success
        val classpath = classpath()
        val resolved = plugins.resolveFor(module, classpath + bootClasspath)
        val r = ctx.withHeartbeat("Compiling Kotlin") {
            compiler.compile(
                kt, sourceFiles(module), classpath, out, levelOf(module.languageLevel),
                bootClasspath = bootClasspath,
                compilerPlugins = resolved.classpaths, pluginOptions = resolved.options,
                commonSources = commonSources(),
                optIns = optIns(),
                status = { ctx.info(it) },
            )
        }
        ctx.reportKotlinProblems(r)
        return if (r.success) TaskResult.Success else TaskResult.Failed(kotlinFailureSummary(r))
    }
}
