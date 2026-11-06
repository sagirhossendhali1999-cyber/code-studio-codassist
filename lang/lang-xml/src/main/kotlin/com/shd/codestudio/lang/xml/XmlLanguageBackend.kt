package com.shd.codestudio.lang.xml

import com.shd.codestudio.lang.BackendCapability
import com.shd.codestudio.lang.CompilationContext
import com.shd.codestudio.lang.LanguageBackend
import com.shd.codestudio.lang.LanguageId
import com.shd.codestudio.lang.SourceAnalyzer

/**
 * The XML language backend (Android layouts, values, manifest, drawables, menus, navigation). Analysis +
 * completion only: XML is not compiled here — `res/` is processed by the Android build system (aapt2), so
 * this backend contributes no compiler. Error recovery is inherent to the tolerant parser; bindings/
 * incremental are not advertised (no symbol model yet; full reparse per change).
 */
class XmlLanguageBackend : LanguageBackend {
    override val id: String = "xml"
    override val languages: Set<LanguageId> = setOf(LANGUAGE_ID)
    override val capabilities: Set<BackendCapability> = setOf(
        BackendCapability.ERROR_RECOVERY,
        BackendCapability.COMPLETION,
        BackendCapability.SEMANTIC_HIGHLIGHT, // structural coloring: namespace prefixes + resource references
        BackendCapability.INLAY_HINTS,        // resolved resource-value previews (host wires the resolver)
        BackendCapability.CODE_FOLDING,       // collapse element bodies + comments (real PSI ranges)
        BackendCapability.SIGNATURE_HELP,     // parameter hints: expected value of the attribute under the caret
        BackendCapability.FORMAT,             // Reformat Code (Android Studio's layout rules per file type)
    )

    override fun createAnalyzer(ctx: CompilationContext): SourceAnalyzer = XmlSourceAnalyzer()

    companion object {
        val LANGUAGE_ID = LanguageId("xml")
    }
}
