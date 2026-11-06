package com.shd.codestudio.lang.jdt

import com.shd.codestudio.lang.BackendCapability
import com.shd.codestudio.lang.CompilationContext
import com.shd.codestudio.lang.LanguageBackend
import com.shd.codestudio.lang.LanguageId
import com.shd.codestudio.lang.SourceAnalyzer

/**
 * The default Java [LanguageBackend], engine = Eclipse JDT. Error-tolerant (statement + binding
 * recovery), so editor features work on broken code; bindings provide the access/modifier info that
 * powers smart completion. Implements the backend-neutral language-api SPI.
 */
class JdtLanguageBackend : LanguageBackend {
    override val id: String = "jdt"
    override val languages: Set<LanguageId> = setOf(LanguageId("java"))
    override val capabilities: Set<BackendCapability> = setOf(
        BackendCapability.ERROR_RECOVERY,
        BackendCapability.BINDINGS,
        BackendCapability.COMPLETION,
        BackendCapability.SNIPPETS,
        BackendCapability.POSTFIX,
        BackendCapability.INLAY_HINTS,
        BackendCapability.SIGNATURE_HELP,
        BackendCapability.SEMANTIC_HIGHLIGHT,
        BackendCapability.CODE_FOLDING,
        BackendCapability.FORMAT,
    )

    override fun createAnalyzer(ctx: CompilationContext): SourceAnalyzer = JdtSourceAnalyzer(ctx)
}
