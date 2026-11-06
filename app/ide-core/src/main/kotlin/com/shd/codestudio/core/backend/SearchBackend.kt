package com.shd.codestudio.core.backend

import com.shd.codestudio.core.BackendContext
import com.shd.codestudio.ui.backend.IndexUiStatus
import com.shd.codestudio.ui.backend.SearchService
import com.shd.codestudio.ui.backend.SymbolHit
import com.shd.codestudio.ui.backend.UiSearchOptions
import com.shd.codestudio.ui.backend.UiTextMatch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

/** [SearchService] over the engine's index: live status + go-to-symbol / member / full-text search. */
internal class SearchBackend(private val ctx: BackendContext) : SearchService {
    override val indexStatus: StateFlow<IndexUiStatus> =
        ctx.engineFlow(IndexUiStatus()) { it.indexStatus }

    override fun reindex() = ctx.services.reindex()

    override suspend fun searchSymbols(query: String, limit: Int): List<SymbolHit> =
        ctx.services.search.searchSymbols(query, limit).map {
            SymbolHit(
                it.name,
                it.container ?: it.kind,
                it.kind,
                ctx.services.search.symbolFilePath(it.fileId),
                it.offset
            )
        }

    override suspend fun searchMembers(query: String, limit: Int): List<SymbolHit> =
        ctx.services.search.searchMembers(query, limit).map {
            SymbolHit(it.name, it.owner.substringAfterLast('.'), it.kind, null, null)
        }

    // Walks/reads files — keep off the caller's (possibly UI) dispatcher.
    override suspend fun findInFiles(
        query: String,
        options: UiSearchOptions,
        limit: Int
    ): List<UiTextMatch> =
        withContext(Dispatchers.IO) { ctx.services.search.findInFiles(query, options, limit) }
}
