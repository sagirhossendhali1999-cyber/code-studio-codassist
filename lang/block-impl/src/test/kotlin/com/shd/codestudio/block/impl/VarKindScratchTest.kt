package com.shd.codestudio.block.impl

import com.shd.codestudio.block.BlockNode
import com.shd.codestudio.block.BlockTree
import com.shd.codestudio.block.SlotCategory
import com.shd.codestudio.lang.AnnotationProcessor
import com.shd.codestudio.lang.CompilationContext
import com.shd.codestudio.lang.dom.NodeKind
import com.shd.codestudio.lang.dom.ParsedFile
import com.shd.codestudio.lang.incremental.DocumentSnapshot
import com.shd.codestudio.lang.jdt.JdtSourceAnalyzer
import com.shd.codestudio.model.ClasspathEntry
import com.shd.codestudio.model.ClasspathEntryKind
import com.shd.codestudio.model.ClasspathSnapshot
import com.shd.codestudio.model.LanguageLevel
import com.shd.codestudio.platform.ContentHash
import com.shd.codestudio.testkit.TestDocument
import com.shd.codestudio.vfs.VirtualFile
import kotlin.test.Test

/** A [VirtualFile] identified by a path with optional in-memory content. */
class VarKindScratchTest {

    private val engine = BlockProjectionEngine.withJava()

    @Test
    fun traceVarDeclarationSlotKinds() {
        val src = """
            class A {
                void m() {
                    var n = 1;
                    var s = "x";
                    int k = 2;
                    java.lang.String q = "y";
                }
            }
        """.trimIndent()
        val tree = project(src)
        // dump every LOCAL_VAR block's slots
        for (b in tree.root.descendants()) {
            if (b.kind == NodeKind.LOCAL_VAR) {
                println("LOCAL_VAR block '${b.range}' text-ish slots:")
                b.slots.forEachIndexed { i, s ->
                    println("  slot[$i] cat=${s.category} valueKind=${s.valueKind} children=${s.children.map { c -> "${c.kind.id}/${c.valueKind}" }}")
                }
            }
        }
    }

    private fun project(src: String): BlockTree = engine.project(parse(src))

    private fun parse(src: String): ParsedFile {
        val file = StubFile("/src/A.java", src)
        return analyzer().incrementalParser.parseFull(Snapshot(file, 1, src))
    }

    private fun analyzer(): JdtSourceAnalyzer {
        val ctx = object : CompilationContext {
            override val sourceRoots: List<VirtualFile> = listOf(StubFile("/src"))
            override val classpath: ClasspathSnapshot = EmptyClasspath
            override val bootClasspath: ClasspathSnapshot = bootOf(System.getProperty("java.home"))
            override val languageLevel = LanguageLevel.JAVA_17
            override val outputDir: VirtualFile = StubFile("/out")
            override val processors: List<AnnotationProcessor> = emptyList()
        }
        return JdtSourceAnalyzer(ctx)
    }

    private fun BlockNode.descendants(): Sequence<BlockNode> = sequence {
        yield(this@descendants)
        for (s in slots) for (c in s.children) yieldAll(c.descendants())
    }

    private object EmptyClasspath : ClasspathSnapshot {
        override val entries: List<ClasspathEntry> = emptyList()
        override fun fingerprint() = ContentHash("")
    }

    private fun bootOf(vararg paths: String) = object : ClasspathSnapshot {
        override val entries = paths.map { ClasspathEntry(StubFile(it), ClasspathEntryKind.SDK_BOOTCLASSPATH) }
        override fun fingerprint() = ContentHash(paths.joinToString())
    }

    private class Snapshot(
        file: VirtualFile,
        version: Long,
        text: CharSequence,
    ) : DocumentSnapshot by TestDocument(text, file, version)
}
