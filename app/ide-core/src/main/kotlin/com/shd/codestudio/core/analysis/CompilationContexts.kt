package com.shd.codestudio.core.analysis

import com.shd.codestudio.lang.CompilationContext
import com.shd.codestudio.lang.CompilationContextProvider
import com.shd.codestudio.lang.LanguageId
import com.shd.codestudio.model.Module
import com.shd.codestudio.model.Workspace

/**
 * Picks the [CompilationContext] a language backend is handed: a plugin's [CompilationContextProvider] if one
 * claims the language and answers, otherwise the host's model-derived context.
 *
 * Its own object so the selection rules are testable without an engine, and because they are the contract a
 * plugin author is told about on [CompilationContextProvider]:
 *
 *  - only providers claiming [language] are asked, in registration order;
 *  - the first non-null answer wins, and a null means "not mine after all";
 *  - a provider that throws is reported through [onError] and skipped, never propagated. Analysis of every
 *    other language in the project must not stop because one plugin's provider is broken.
 */
internal object CompilationContexts {

    fun resolve(
        providers: List<CompilationContextProvider>,
        workspace: Workspace,
        module: Module,
        language: LanguageId,
        variant: Set<String>?,
        onError: (CompilationContextProvider, Throwable) -> Unit = { _, _ -> },
        fallback: () -> CompilationContext,
    ): CompilationContext {
        for (provider in providers) {
            if (language !in provider.languages) continue
            val contributed = try {
                provider.contextFor(workspace, module, language, variant)
            } catch (t: Throwable) {
                if (t is VirtualMachineError) throw t
                onError(provider, t)
                null
            }
            if (contributed != null) return contributed
        }
        return fallback()
    }
}
