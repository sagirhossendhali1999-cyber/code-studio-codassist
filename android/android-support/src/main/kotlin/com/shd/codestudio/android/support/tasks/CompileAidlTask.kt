package com.shd.codestudio.android.support.tasks

import com.shd.codestudio.android.support.aidl.AidlCompileRequest
import com.shd.codestudio.android.support.aidl.AidlCompiler
import com.shd.codestudio.android.support.aidl.AidlDiagnostic
import com.shd.codestudio.android.support.aidl.AidlSeverity
import com.shd.codestudio.build.BuildDiagnostic
import com.shd.codestudio.build.BuildSeverity
import com.shd.codestudio.build.DiagnosticKind
import com.shd.codestudio.build.DiagnosticLocation
import com.shd.codestudio.build.Task
import com.shd.codestudio.build.TaskContext
import com.shd.codestudio.build.TaskInputs
import com.shd.codestudio.build.TaskInputsImpl
import com.shd.codestudio.build.TaskName
import com.shd.codestudio.build.TaskOutputs
import com.shd.codestudio.build.TaskOutputsImpl
import com.shd.codestudio.build.TaskResult
import com.shd.codestudio.build.engine.debug
import java.nio.file.Path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * `compileAidl<Variant>`: generate the Java `IInterface`/`Stub`/`Proxy` for the module's `.aidl` files, into a
 * generated-source root that both compile tasks then read.
 *
 * AGP's equivalent shells out to the SDK's `aidl` binary. This runs [AidlCompiler], a Kotlin
 * implementation, instead, because `build-tools` ships that binary only for linux-x86_64 and this build has to
 * run on an Android device. See [com.shd.codestudio.android.support.aidl.AidlCompiler].
 *
 * Fingerprinting is over the `.aidl` files themselves, source and import roots alike: an edit to a dependency's
 * `parcelable` declaration changes what this module generates, so it has to invalidate this task too.
 */
internal class CompileAidlTask(
    override val name: TaskName,
    /** The module's own `aidl/` roots; everything below them is generated from. */
    private val sourceRoots: List<Path>,
    /** Dependency + AAR `aidl/` folders, contributing types only. */
    private val importRoots: List<Path>,
    /** The SDK's `platforms/android-NN/framework.aidl`, when the host has one on disk. */
    private val frameworkAidl: Path?,
    /** Lazy: the compile classpath, consulted only for framework types no `framework.aidl` declared. */
    private val classpath: () -> List<Path>,
    private val outDir: Path,
) : Task {

    private fun aidlFiles(): List<Path> = (sourceRoots + importRoots).flatMap { AidlCompiler.aidlFilesUnder(it) }

    override val inputs: TaskInputs
        get() = TaskInputsImpl().apply {
            filePaths("aidl", aidlFiles())
            property("framework", frameworkAidl?.toString().orEmpty())
        }

    override val outputs: TaskOutputs get() = TaskOutputsImpl().apply { dirPath("aidlGen", outDir) }

    override suspend fun execute(ctx: TaskContext): TaskResult {
        ctx.checkCanceled()
        val result = withContext(Dispatchers.IO) {
            AidlCompiler.compile(
                AidlCompileRequest(
                    sourceRoots = sourceRoots,
                    importRoots = importRoots,
                    frameworkAidl = frameworkAidl,
                    classpath = classpath(),
                    outputDir = outDir,
                )
            )
        }
        for (diagnostic in result.diagnostics) ctx.diagnostics.report(diagnostic.toBuildDiagnostic())
        if (result.hasErrors) {
            val first = result.diagnostics.first { it.severity == AidlSeverity.ERROR }
            return TaskResult.Failed("AIDL compilation failed: $first")
        }
        if (result.generated.isNotEmpty()) ctx.debug("Generated ${result.generated.size} Java file(s) from AIDL")
        return TaskResult.Success
    }

    private fun AidlDiagnostic.toBuildDiagnostic() = BuildDiagnostic(
        severity = if (severity == AidlSeverity.ERROR) BuildSeverity.ERROR else BuildSeverity.WARNING,
        message = message,
        kind = DiagnosticKind.COMPILER,
        source = "aidl",
        location = file.takeIf { it.isNotEmpty() }?.let { DiagnosticLocation(it, pos.line, pos.column) },
    )
}
