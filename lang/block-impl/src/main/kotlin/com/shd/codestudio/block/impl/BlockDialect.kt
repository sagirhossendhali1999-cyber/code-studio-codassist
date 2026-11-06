package com.shd.codestudio.block.impl

import com.shd.codestudio.block.BlockField
import com.shd.codestudio.block.BlockMapping
import com.shd.codestudio.block.ProjectionContext
import com.shd.codestudio.block.SlotCategory
import com.shd.codestudio.block.ValueKind
import com.shd.codestudio.lang.LanguageId
import com.shd.codestudio.lang.dom.DomNode
import com.shd.codestudio.lang.dom.NodeKind
import com.shd.codestudio.lang.dom.TextRange

/**
 * The per-language reading of node kinds that the projection pass needs beside a mapping's decomposition:
 * the header label the UI keys off, which slot category a child satisfies, the role of a leaf's token, and
 * the syntactic value-kind guesses that pick socket shapes. Java and Kotlin share several neutral kinds
 * ([NodeKind.BLOCK], [NodeKind.METHOD_CALL]) but not their meaning around them, so each language brings its
 * own.
 */
internal interface BlockDialect {
    fun labelFor(kind: NodeKind): String
    fun categoryFor(kind: NodeKind): SlotCategory
    fun roleFor(kind: NodeKind): String
    fun valueKindFor(node: DomNode): ValueKind
    fun expectedValueKind(parent: DomNode, child: DomNode): ValueKind
}

/** The dialect a built-in mapping is read with; a plugin's mapping is read as Java. */
internal fun dialectOf(mapping: BlockMapping): BlockDialect? = when (mapping) {
    KotlinBlockMapping -> KotlinDialect
    JavaBlockMapping -> JavaDialect
    else -> null
}

internal object JavaDialect : BlockDialect {
    override fun labelFor(kind: NodeKind) = com.shd.codestudio.block.impl.labelFor(kind)
    override fun categoryFor(kind: NodeKind) = com.shd.codestudio.block.impl.categoryFor(kind)
    override fun roleFor(kind: NodeKind) = com.shd.codestudio.block.impl.roleFor(kind)
    override fun valueKindFor(node: DomNode) = com.shd.codestudio.block.impl.valueKindFor(node)
    override fun expectedValueKind(parent: DomNode, child: DomNode) = com.shd.codestudio.block.impl.expectedValueKind(parent, child)
}

/** The language a parsed file is in, from its extension (the DOM itself carries no language tag). */
internal fun languageOfPath(path: String): LanguageId = when (path.substringAfterLast('.', "").lowercase()) {
    "kt", "kts" -> LanguageId("kotlin")
    else -> LanguageId("java")
}

/** A read-only chrome field over [range]'s source. */
internal fun ProjectionContext.chromeField(range: TextRange): BlockField =
    field(role = "syntax", text = textOf(range).toString(), editable = false, range = range)

/** Produced-kind lookup: the engine's pass resolves it (oracle first, then its dialect). */
internal fun ProjectionContext.produced(node: DomNode, dialect: BlockDialect = JavaDialect): ValueKind =
    (this as? ValueKindResolver)?.produced(node) ?: dialect.valueKindFor(node)
