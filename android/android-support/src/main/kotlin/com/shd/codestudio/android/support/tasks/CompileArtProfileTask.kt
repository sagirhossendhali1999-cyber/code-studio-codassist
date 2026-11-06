package com.shd.codestudio.android.support.tasks

import com.shd.codestudio.android.support.tools.ArtProfiles
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
import com.shd.codestudio.build.engine.warn
import java.nio.file.Files
import java.nio.file.Path

/**
 * `compile<Variant>ArtProfile`: merge the Baseline Profile rules of the app, its library modules and its AARs,
 * and compile them against the dex about to be packaged into [profileOut] / [metadataOut] ([ArtProfiles]).
 * Release builds only, as with AGP: ART does not AOT-compile a debuggable app, so a debug APK has no use for it.
 *
 * Produces nothing (and removes an earlier build's output) when there are no rules, so the packagers can key
 * on whether the files exist. [mapping] is R8's `mapping.txt` on a minified build.
 */
internal class CompileArtProfileTask(
    override val name: TaskName,
    private val rules: List<ArtProfiles.RulesFile>,
    private val dexDirs: List<Path>,
    private val mapping: Path?,
    private val mergedOut: Path,
    private val profileOut: Path,
    private val metadataOut: Path,
) : Task {
    override val inputs: TaskInputs
        get() = TaskInputsImpl().apply {
            filePaths("rules", rules.map { it.path })
            dirPaths("dex", dexDirs)
            filePaths("mapping", listOfNotNull(mapping).filter { Files.exists(it) })
        }
    override val outputs: TaskOutputs
        get() = TaskOutputsImpl().apply {
            filePath("merged", mergedOut)
            filePath("profile", profileOut)
            filePath("metadata", metadataOut)
        }

    override suspend fun execute(ctx: TaskContext): TaskResult {
        ctx.checkCanceled()
        val result = runCatching {
            ArtProfiles.compile(rules, ApkPackaging.packagedDex(dexDirs), mapping, mergedOut, profileOut, metadataOut)
        }.getOrElse { return TaskResult.Failed("Baseline Profile compilation failed: ${it.message}", it) }

        val warnings = when (result) {
            is ArtProfiles.Result.Compiled -> result.warnings
            is ArtProfiles.Result.Empty -> result.warnings
            is ArtProfiles.Result.Failed -> emptyList()
        }
        for (w in warnings) {
            ctx.warn("${name.value}: $w")
            ctx.diagnostics.report(BuildDiagnostic(BuildSeverity.WARNING, w, DiagnosticKind.PACKAGING, source = "profgen"))
        }
        return when (result) {
            is ArtProfiles.Result.Compiled -> {
                ctx.debug(
                    "${name.value}: Baseline Profile covers ${result.methods} method(s) and ${result.classes} " +
                        "class(es) from ${rules.size} rules file(s)",
                )
                TaskResult.Success
            }
            is ArtProfiles.Result.Empty -> {
                ctx.debug("${name.value}: no Baseline Profile rules matched; packaging no profile")
                TaskResult.Success
            }
            is ArtProfiles.Result.Failed -> {
                for (e in result.errors) {
                    ctx.diagnostics.report(
                        BuildDiagnostic(
                            BuildSeverity.ERROR, "Baseline Profile rule does not parse", DiagnosticKind.PACKAGING,
                            source = "profgen",
                            location = DiagnosticLocation(e.file.toString(), e.line, e.column),
                            detail = e.message,
                        ),
                    )
                }
                TaskResult.Failed("Baseline Profile rules do not parse:\n" + result.errors.joinToString("\n"))
            }
        }
    }
}
