package com.shd.codestudio.lang.xml

import com.shd.codestudio.lang.AnalysisResult
import com.shd.codestudio.lang.SourceAnalyzer
import com.shd.codestudio.lang.completion.CompletionContribution
import com.shd.codestudio.lang.dom.DomNode
import com.shd.codestudio.lang.dom.ParsedFile
import com.shd.codestudio.lang.folding.FoldingService
import com.shd.codestudio.lang.formatting.FormattingService
import com.shd.codestudio.lang.hints.InlayHintService
import com.shd.codestudio.lang.incremental.DocumentEdit
import com.shd.codestudio.lang.incremental.DocumentSnapshot
import com.shd.codestudio.lang.incremental.IncrementalParser
import com.shd.codestudio.lang.incremental.ReparseResult
import com.shd.codestudio.lang.resolve.ResolveResult
import com.shd.codestudio.lang.resolve.Scope
import com.shd.codestudio.lang.resolve.StructureItem
import com.shd.codestudio.lang.resolve.Symbol
import com.shd.codestudio.lang.resolve.SymbolFilter
import com.shd.codestudio.lang.resolve.TypeRef
import com.shd.codestudio.lang.signature.SignatureHelpService
import com.shd.codestudio.lang.signature.SignatureInfo
import com.shd.codestudio.lang.xml.completion.XmlCompletionContributor
import com.shd.codestudio.lang.xml.completion.XmlCompletion
import com.shd.codestudio.lang.xml.completion.XmlCompletionPosition
import com.shd.codestudio.lang.xml.folding.XmlFoldingService
import com.shd.codestudio.lang.xml.formatting.XmlFormattingService
import com.shd.codestudio.lang.xml.highlight.XmlSemanticHighlighter
import com.shd.codestudio.lang.xml.hints.XmlInlayHintService
import com.shd.codestudio.lang.xml.hints.XmlResourceValueResolver
import com.shd.codestudio.lang.xml.signature.XmlSignatureHelp
import com.shd.codestudio.lang.xml.structure.XmlStructure
import com.shd.codestudio.vfs.VirtualFile
import java.util.concurrent.ConcurrentHashMap

/**
 * The editor-time engine for XML. Tolerant parsing + completion are live; symbol resolution is not
 * modeled (XML has no Java-style scope), so [resolve]/[scopeAt]/[expectedTypeAt] return empty
 * results. Well-formedness diagnostics come straight from the parser and are surfaced via [analyze].
 *
 * [contributors] is the injection seam the host fills (e.g. an Android widget + resource contributor),
 * mirroring how the JDT analyzer receives its index service. The cached last-parse per file lets the
 * host's "parseFull then analyze" call sequence return the diagnostics from that exact parse.
 */
class XmlSourceAnalyzer : SourceAnalyzer {

    /** Set by the host after construction. Empty ⇒ completion returns nothing (no Android knowledge here). */
    @Volatile
    var contributors: List<XmlCompletionContributor> = emptyList()

    /** Set by the host: resolves a local `@type/name` to its value for inlay hints. Null ⇒ no inlay hints. */
    @Volatile
    var inlayResourceResolver: XmlResourceValueResolver? = null

    /** Set by the host: describes an attribute's expected value (enum/flags/boolean/refs) for signature help
     *  when the caret is inside `="…"`. Null ⇒ no parameter hints (no Android schema wired). */
    @Volatile
    var valueHintProvider: ((XmlCompletionPosition) -> SignatureInfo?)? = null

    private val backing = XmlIncrementalParser()
    private val lastByFile = ConcurrentHashMap<String, ParsedFile>()

    override val incrementalParser: IncrementalParser = object : IncrementalParser {
        override fun parseFull(snapshot: DocumentSnapshot): ParsedFile =
            backing.parseFull(snapshot).also { lastByFile[snapshot.file.path] = it }

        override fun reparse(
            previous: ParsedFile, newSnapshot: DocumentSnapshot, edits: List<DocumentEdit>
        ): ReparseResult = backing.reparse(previous, newSnapshot, edits)
            .also { lastByFile[newSnapshot.file.path] = it.tree }
    }

    private val completionContributor = XmlCompletion(contributors = { contributors })
    override fun completionContributions(): List<CompletionContribution> =
        listOf(CompletionContribution(completionContributor))

    /** Type-aware coloring is structural over the DOM (namespace prefixes + resource references), always on. */
    override val semanticHighlighter = XmlSemanticHighlighter(::parsedFile)

    /** Code folding (element bodies + comments), always on — ranges come straight from the PSI. */
    override val folding: FoldingService = XmlFoldingService(::parsedFile)

    /** Reformat Code: re-indents and re-wraps tags with Android Studio's per-file-type rules. */
    override val formatting: FormattingService = XmlFormattingService()

    /** Parameter hints inside an attribute value — available only once the host wires the schema resolver. */
    override val signatureHelp: SignatureHelpService?
        get() = valueHintProvider?.let {
            XmlSignatureHelp({ ds -> incrementalParser.parseFull(ds) }, it)
        }

    /** Inlay hints (resolved resource-value previews) are available only once the host wires the resolver. */
    override val inlayHints: InlayHintService?
        get() = inlayResourceResolver?.let { XmlInlayHintService(::parsedFile, it) }

    override suspend fun parsedFile(file: VirtualFile): ParsedFile =
        lastByFile[file.path] ?: incrementalParser.parseFull(EmptyDocument(file))

    override suspend fun analyze(file: VirtualFile): AnalysisResult {
        val parsed = lastByFile[file.path]
        return AnalysisResult(file, parsed?.diagnostics ?: emptyList())
    }

    /** The element tree, for the outline and the sticky headers (see [XmlStructure]). */
    override fun fileStructure(file: VirtualFile, text: CharSequence): List<StructureItem> =
        runCatching { XmlStructure.of(XmlTreeParser.parse(file.path, text)) }.getOrDefault(emptyList())

    override fun resolve(node: DomNode): ResolveResult = ResolveResult.Unresolved
    override fun scopeAt(file: VirtualFile, offset: Int): Scope = EmptyScope
    override fun expectedTypeAt(file: VirtualFile, offset: Int): TypeRef? = null

    private class EmptyDocument(override val file: VirtualFile) : DocumentSnapshot {
        override val version: Long = 0
        override val text: CharSequence = ""
        override fun length(): Int = 0
    }

    private object EmptyScope : Scope {
        override val enclosing: Scope? = null
        override fun symbols(filter: SymbolFilter): List<Symbol> = emptyList()
        override fun resolve(name: String): ResolveResult = ResolveResult.Unresolved
    }
}
