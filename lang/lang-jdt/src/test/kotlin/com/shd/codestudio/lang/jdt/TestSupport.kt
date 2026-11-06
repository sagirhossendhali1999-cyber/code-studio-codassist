package com.shd.codestudio.lang.jdt

import com.shd.codestudio.lang.AnnotationProcessor
import com.shd.codestudio.lang.CompilationContext
import com.shd.codestudio.lang.completion.CompletionRequest
import com.shd.codestudio.lang.completion.CompletionResult
import com.shd.codestudio.lang.completion.CompletionTrigger
import com.shd.codestudio.lang.completion.complete
import com.shd.codestudio.model.ClasspathEntry
import com.shd.codestudio.model.ClasspathEntryKind
import com.shd.codestudio.model.ClasspathSnapshot
import com.shd.codestudio.model.LanguageLevel
import com.shd.codestudio.platform.ContentHash
import com.shd.codestudio.testkit.InMemoryVirtualFile
import com.shd.codestudio.testkit.TestDocument
import com.shd.codestudio.testkit.caret
import com.shd.codestudio.testkit.labels
import com.shd.codestudio.testkit.writeSource
import com.shd.codestudio.vfs.VirtualFile
import java.nio.file.Files
import java.nio.file.Path

/** Drive a `suspend` [block] to completion synchronously — package-level so callers need no import. */
fun <T> runSync(block: suspend () -> T): T = com.shd.codestudio.testkit.runSync(block)

/** A [VirtualFile] identified by a path with optional in-memory content. */
typealias StubFile = InMemoryVirtualFile

private object EmptyClasspath : ClasspathSnapshot {
    override val entries: List<ClasspathEntry> = emptyList()
    override fun fingerprint() = ContentHash("")
}

private fun bootOf(vararg paths: String) = object : ClasspathSnapshot {
    override val entries = paths.map { ClasspathEntry(StubFile(it), ClasspathEntryKind.SDK_BOOTCLASSPATH) }
    override fun fingerprint() = ContentHash(paths.joinToString())
}

/** An analyzer whose sourcepath is [sourceDirs] and whose platform is the current JDK's jrt image. */
fun analyzer(sourceDirs: List<Path>): JdtSourceAnalyzer {
    val ctx = object : CompilationContext {
        override val sourceRoots: List<VirtualFile> = sourceDirs.map { StubFile(it.toString()) }
        override val classpath: ClasspathSnapshot = EmptyClasspath
        override val bootClasspath: ClasspathSnapshot = bootOf(System.getProperty("java.home"))
        override val languageLevel = LanguageLevel.JAVA_17
        override val outputDir: VirtualFile = StubFile("/out")
        override val processors: List<AnnotationProcessor> = emptyList()
    }
    return JdtSourceAnalyzer(ctx)
}

/** Writes [files] (relPath -> content) under a fresh temp dir and returns (analyzer, dir). */
fun workspaceWith(vararg files: Pair<String, String>): Pair<JdtSourceAnalyzer, Path> {
    val dir = Files.createTempDirectory("jdt-test")
    for ((rel, content) in files) dir.writeSource(rel, content, trim = false)
    return analyzer(listOf(dir)) to dir
}

/**
 * Completes [codeWithCaret] (a `|CARET|` marks the caret) as if editing [file]; returns each item's bare
 * name. A method's insertText now carries `()`, so we take the part before `(` — the identifier these
 * member-presence / ranking tests care about (the `()` insertion itself is covered by CompletionInsertionTest).
 */
fun completeLabels(analyzer: JdtSourceAnalyzer, file: Path, codeWithCaret: String): List<String> {
    val (text, offset) = caret(codeWithCaret, "|CARET|")
    val request = CompletionRequest(TestDocument(text, StubFile(file.toString(), text)), offset, CompletionTrigger.Explicit)
    return runSync { analyzer.complete(request) }.labels()
}

/** Like [completeLabels] but returns the full [CompletionResult] (to inspect kinds, detail, edits). */
fun completeResult(analyzer: JdtSourceAnalyzer, file: Path, codeWithCaret: String): CompletionResult {
    val (text, offset) = caret(codeWithCaret, "|CARET|")
    val request = CompletionRequest(TestDocument(text, StubFile(file.toString(), text)), offset, CompletionTrigger.Explicit)
    return runSync { analyzer.complete(request) }
}

/** Signature help at the `|CARET|` in [codeWithCaret], as if editing [file]. */
fun signatureHelpAt(analyzer: JdtSourceAnalyzer, file: Path, codeWithCaret: String): com.shd.codestudio.lang.signature.SignatureHelp? {
    val (text, offset) = caret(codeWithCaret, "|CARET|")
    val request = com.shd.codestudio.lang.signature.SignatureHelpRequest(
        TestDocument(text, StubFile(file.toString(), text)), offset, com.shd.codestudio.lang.signature.SignatureHelpTrigger.Explicit,
    )
    return runSync { analyzer.signatureHelp!!.signatureHelp(request) }
}
