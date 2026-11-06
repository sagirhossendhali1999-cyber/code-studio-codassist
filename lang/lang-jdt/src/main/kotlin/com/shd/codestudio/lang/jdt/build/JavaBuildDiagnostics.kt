package com.shd.codestudio.lang.jdt.build

import com.shd.codestudio.build.BuildDiagnostic
import com.shd.codestudio.build.BuildSeverity
import com.shd.codestudio.build.DiagnosticKind
import com.shd.codestudio.build.DiagnosticLocation
import com.shd.codestudio.build.TaskContext
import com.shd.codestudio.build.engine.reportAll
import com.shd.codestudio.build.engine.transcript
import com.shd.codestudio.lang.jdt.compile.JdtBatchCompiler

/**
 * Surface an ecj compile's result in the build console: its problems as structured, navigable diagnostics
 * and its printed report as the (DEBUG) transcript behind them.
 *
 * ecj hands every problem to an `ICompilerRequestor` as a `CategorizedProblem` — file, line, column, the
 * problem id, the offending source line. That is what a user sees here. The compiler's *printed* report is
 * a rendering of the same problems with the column and id thrown away, so it is kept only as the record of
 * what the tool said, never as the thing we parse to find out what went wrong.
 *
 * Shared by the two `compileJava` tasks (the plain JVM one and the Android variant), which differ in how
 * they assemble the classpath, not in how a Java error should read.
 */
fun TaskContext.reportJavaProblems(result: JdtBatchCompiler.Result) {
    reportAll(result.diagnostics.map { it.toBuildDiagnostic() })
    transcript(result.messages)
}

/** One line naming what failed, for `TaskResult.Failed`; the problems themselves are already reported. */
fun javaFailureSummary(result: JdtBatchCompiler.Result): String {
    val errors = result.diagnostics.filter { it.isError }
    return when (errors.size) {
        0 -> "Java compilation failed"
        1 -> errors.first().message.trim()
        else -> "Java compilation failed with ${errors.size} errors"
    }
}

private fun JdtBatchCompiler.Diagnostic.toBuildDiagnostic(): BuildDiagnostic = BuildDiagnostic(
    severity = if (isError) BuildSeverity.ERROR else BuildSeverity.WARNING,
    message = message.trim(),
    kind = DiagnosticKind.COMPILER,
    source = "java",
    location = file?.let { DiagnosticLocation(it, line ?: -1, column) },
    code = code,
    detail = snippet,
)
