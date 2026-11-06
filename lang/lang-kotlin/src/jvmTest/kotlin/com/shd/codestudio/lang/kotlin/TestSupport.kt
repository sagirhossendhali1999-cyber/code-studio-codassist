package com.shd.codestudio.lang.kotlin

import com.shd.codestudio.lang.dom.DomNode
import com.shd.codestudio.lang.incremental.DocumentSnapshot
import com.shd.codestudio.lang.kotlin.parse.KotlinIncrementalParser
import com.shd.codestudio.lang.kotlin.parse.KotlinParsedFile
import com.shd.codestudio.testkit.InMemoryVirtualFile
import com.shd.codestudio.testkit.TestDocument
import com.shd.codestudio.vfs.VirtualFile

/** A bare [VirtualFile] backed only by a path — enough for parser/completion tests. */
typealias FakeFile = InMemoryVirtualFile

/** A [DocumentSnapshot] whose file defaults to `src/Main.kt` for one-liner parse/completion tests. */
class TestDoc(
    text: CharSequence,
    file: VirtualFile = FakeFile("src/Main.kt"),
    version: Long = 1,
) : DocumentSnapshot by TestDocument(text, file, version)

fun parse(kotlin: String, path: String = "src/Main.kt"): KotlinParsedFile =
    KotlinIncrementalParser().parseFull(TestDoc(kotlin, FakeFile(path))) as KotlinParsedFile

/** Pre-order flatten of the neutral DOM, for assertions. */
fun DomNode.flatten(): List<DomNode> = buildList {
    fun walk(n: DomNode) { add(n); n.children.forEach(::walk) }
    walk(this@flatten)
}
