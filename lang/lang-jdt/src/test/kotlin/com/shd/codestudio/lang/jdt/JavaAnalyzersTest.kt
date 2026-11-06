package com.shd.codestudio.lang.jdt

import com.shd.codestudio.analysis.AnalysisTarget
import com.shd.codestudio.analysis.DiagnosticSink
import com.shd.codestudio.analysis.DiagnosticTag
import com.shd.codestudio.analysis.NodeIndex
import com.shd.codestudio.analysis.QuickFix
import com.shd.codestudio.analysis.RelatedRange
import com.shd.codestudio.index.IndexService
import com.shd.codestudio.lang.SourceAnalyzer
import com.shd.codestudio.lang.dom.ParsedFile
import com.shd.codestudio.lang.dom.Severity
import com.shd.codestudio.lang.dom.TextRange
import com.shd.codestudio.lang.jdt.analysis.UnusedImportAnalyzer
import com.shd.codestudio.model.Module
import com.shd.codestudio.vfs.VirtualFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The built-in Java [com.shd.codestudio.analysis.FileAnalyzer], driven the way the engine drives it (over the pass's
 * shared [NodeIndex]). It is a syntactic heuristic with a boundary rule that is easy to get subtly wrong (an
 * import of `List` must not read as used because the body mentions `ListView`), so the boundaries are what
 * these cover.
 */
class JavaAnalyzersTest {

    // ---- java.unusedImport ----

    @Test
    fun flagsAnUnusedSingleTypeImport() {
        val src = """
            package app;
            import java.util.List;
            import java.util.Map;
            class T { Map<String, String> m; }
        """.trimIndent()
        val found = unusedImports(src)
        assertEquals(listOf("Unused import 'List'"), found.map { it.message })
        assertTrue(DiagnosticTag.UNUSED in found.single().tags, "the fix's muted rendering keys on the UNUSED tag")
        assertEquals("import java.util.List;", src.substring(found.single().range.start, found.single().range.end))
    }

    @Test
    fun leavesWildcardAndStaticImportsAlone() {
        val src = """
            package app;
            import java.util.*;
            import static java.util.Arrays.asList;
            class T {}
        """.trimIndent()
        assertEquals(emptyList(), unusedImports(src).map { it.message })
    }

    @Test
    fun aNameUsedOnlyInAnotherImportLineIsStillUnused() {
        // `List` appears in the file only inside import declarations, so it is not used by the code.
        val src = """
            package app;
            import java.util.List;
            import java.util.ListIterator;
            class T { ListIterator<String> it; }
        """.trimIndent()
        assertEquals(listOf("Unused import 'List'"), unusedImports(src).map { it.message })
    }

    @Test
    fun aNameIsUsedOnlyAsAWholeIdentifier() {
        // `ListView` must not count as a use of `List`: the boundary this analyzer's whole answer rests on.
        val src = """
            package app;
            import java.util.List;
            class T { ListView v; }
            class ListView {}
        """.trimIndent()
        assertEquals(listOf("Unused import 'List'"), unusedImports(src).map { it.message })
    }

    @Test
    fun aMentionInACommentOrStringCountsAsUse() {
        // The documented heuristic: no resolution, so any identifier token outside the imports counts.
        val src = """
            package app;
            import java.util.List;
            class T { /* returns a List one day */ void m() {} }
        """.trimIndent()
        assertEquals(emptyList(), unusedImports(src).map { it.message })
    }

    // ---- harness ----

    private fun unusedImports(src: String) = run(src) { target, sink, nodes ->
        UnusedImportAnalyzer().analyze(target, sink, nodes)
    }

    private fun run(
        src: String, analyze: (AnalysisTarget, DiagnosticSink, NodeIndex) -> Unit,
    ): List<Finding> {
        val (analyzer, dir) = workspaceWith("app/Empty.java" to "package app; class Empty {}")
        return try {
            val file = StubFile(dir.resolve("app/T.java").toString(), src)
            val parsed = analyzer.parseSyntactic(file, src)
            val sink = RecordingSink()
            analyze(Target(file, parsed, analyzer), sink, NodeIndex.over(parsed))
            sink.found
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    private class Finding(val range: TextRange, val message: String, val code: String?, val tags: Set<DiagnosticTag>) {
        override fun toString() = "$code@${range.start}..${range.end}: $message"
    }

    private class RecordingSink : DiagnosticSink {
        val found = ArrayList<Finding>()
        override fun report(
            range: TextRange, severity: Severity, message: String, code: String?,
            fixes: List<QuickFix>, tags: Set<DiagnosticTag>, related: List<RelatedRange>,
        ) { found += Finding(range, message, code, tags) }
    }

    private class Target(
        override val file: VirtualFile,
        override val parsed: ParsedFile,
        override val resolver: SourceAnalyzer,
    ) : AnalysisTarget {
        override val documentVersion = 1L
        override val index: IndexService get() = error("these analyzers do not query the index")
        override val module: Module get() = error("these analyzers do not read the module")
        override fun checkCanceled() {}
    }
}
