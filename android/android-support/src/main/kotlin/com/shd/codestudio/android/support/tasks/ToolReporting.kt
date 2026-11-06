package com.shd.codestudio.android.support.tasks

import com.shd.codestudio.android.support.tools.ToolDiagnostic
import com.shd.codestudio.android.support.tools.ToolResult
import com.shd.codestudio.android.support.tools.ToolSeverity
import com.shd.codestudio.build.BuildDiagnostic
import com.shd.codestudio.build.BuildSeverity
import com.shd.codestudio.build.DiagnosticKind
import com.shd.codestudio.build.DiagnosticLocation
import com.shd.codestudio.build.TaskContext
import com.shd.codestudio.build.engine.reportAll
import com.shd.codestudio.build.engine.toolOutput
import com.shd.codestudio.build.engine.transcript

/**
 * Report what an Android build tool found, preferring the tool's own answer to a reading of its output.
 *
 * An in-process D8/R8 hands every problem to a `DiagnosticsHandler` with the origin and position attached
 * ([ToolResult.diagnostics]); those go straight through as structured, navigable Problems and the tool's
 * printed lines stay behind as the DEBUG transcript. A tool with nothing to hand over — aapt2, apksigner,
 * bundletool, a dexer running in a forked VM, all of which reach us as merged stdout/stderr — falls back to
 * [toolOutput], which humanizes the text and parses what it can out of it.
 *
 * Either way the caller writes one line and the console gets one copy of each problem.
 */
internal fun TaskContext.reportTool(
    source: String,
    result: ToolResult,
    kind: DiagnosticKind = DiagnosticKind.GENERIC,
) {
    if (result.diagnostics.isEmpty()) {
        toolOutput(source, result.log, kind)
        return
    }
    reportAll(result.diagnostics.map { it.toBuildDiagnostic(source, kind) })
    transcript(result.log)
}

/** As [reportTool], for a tool whose output we only have as text (no [ToolResult] wrapper). */
internal fun TaskContext.reportToolLines(
    source: String,
    lines: List<String>,
    kind: DiagnosticKind = DiagnosticKind.GENERIC,
) = toolOutput(source, lines, kind)

private fun ToolDiagnostic.toBuildDiagnostic(source: String, kind: DiagnosticKind): BuildDiagnostic =
    BuildDiagnostic(
        severity = when (severity) {
            ToolSeverity.ERROR -> BuildSeverity.ERROR
            ToolSeverity.WARNING -> BuildSeverity.WARNING
            ToolSeverity.INFO -> BuildSeverity.INFO
        },
        message = message.trim(),
        kind = kind,
        source = source,
        location = path?.let { DiagnosticLocation(it, line, column) },
    )
