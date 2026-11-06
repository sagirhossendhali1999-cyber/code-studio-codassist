package com.shd.codestudio.block.impl

import com.shd.codestudio.block.BlockNode
import com.shd.codestudio.block.BlockPart
import com.shd.codestudio.block.BlockTree
import com.shd.codestudio.block.ReplaceWithText
import com.shd.codestudio.block.SlotCategory
import com.shd.codestudio.block.SlotRef
import com.shd.codestudio.lang.AnnotationProcessor
import com.shd.codestudio.lang.CompilationContext
import com.shd.codestudio.lang.dom.NodeKind
import com.shd.codestudio.lang.dom.ParsedFile
import com.shd.codestudio.lang.incremental.DocumentEdit
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
/** TEMP verification harness — prints the projection of super.<String>foo() etc. */
class SuperTypeArgVerifyTest {

    private val engine = BlockProjectionEngine.withJava()

    @Test
    fun traceSuperTypeArg() {
        val src = """
            class A extends B {
                void m() {
                    super.<String>foo();
                }
            }
            class B { <T> void foo() {} }
        """.trimIndent()
        val tree = project(src)
        val call = tree.find { it.kind == NodeKind.METHOD_CALL }
        println("=== super.<String>foo() call block ===")
        println(call?.let { dump(it) } ?: "NO METHOD_CALL BLOCK (carved away?)")

        if (call != null) {
            // find an EXPRESSION-category single slot (the claimed mis-projected base slot)
            val exprSlotIdx = call.slots.indexOfFirst { it.category == SlotCategory.EXPRESSION }
            println("expression slot index: $exprSlotIdx")
            if (exprSlotIdx >= 0) {
                val edits = engine.computeEdit(tree, src, ReplaceWithText(SlotRef(call.id, exprSlotIdx), text = "getThing()"))
                val result = applyEdits(src, edits)
                println("=== after ReplaceWithText on EXPRESSION slot ===")
                println(result)
            }
        }
    }

    @Test
    fun traceControls() {
        for (snippet in listOf("Collections.<String>emptyList();", "this.<String>foo();", "super.foo();")) {
            val src = """
                class A {
                    void m() {
                        $snippet
                    }
                }
            """.trimIndent()
            val tree = project(src)
            val call = tree.find { it.kind == NodeKind.METHOD_CALL }
            println("=== $snippet ===")
            println(call?.let { dump(it) } ?: "no METHOD_CALL block")
        }
    }

    private fun dump(b: BlockNode, indent: String = ""): String = buildString {
        append(indent).append("BLOCK kind=").append(b.kind.id).append(" label='").append(b.label).append("'\n")
        b.parts.forEachIndexed { i, p ->
            when (p) {
                is BlockPart.Field -> append(indent).append("  F[").append(i).append("] role=").append(p.field.role)
                    .append(" editable=").append(p.field.editable).append(" text='").append(p.field.text).append("'\n")
                is BlockPart.Slot -> {
                    append(indent).append("  S[").append(i).append("] cat=").append(p.slot.category)
                        .append(" valueKind=").append(p.slot.valueKind).append(" multiple=").append(p.slot.multiple).append("\n")
                    p.slot.children.forEach { append(dump(it, "$indent    ")) }
                }
            }
        }
    }

    // ---- helpers copied from BlockProjectionTest ----

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

    private fun applyEdits(text: String, edits: List<DocumentEdit>): String {
        val sb = StringBuilder(text)
        for (e in edits.sortedByDescending { it.offset }) {
            sb.replace(e.offset, e.offset + e.oldLength, e.newText.toString())
        }
        return sb.toString()
    }

    private fun BlockTree.find(p: (BlockNode) -> Boolean): BlockNode? = root.descendants().firstOrNull(p)

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
