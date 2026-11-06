package com.shd.codestudio.core

import com.shd.codestudio.core.analysis.ModuleAnalyzers
import com.shd.codestudio.lang.AnalysisResult
import com.shd.codestudio.lang.CacheInvalidation
import com.shd.codestudio.lang.LanguageId
import com.shd.codestudio.lang.SourceAnalyzer
import com.shd.codestudio.lang.dom.DomNode
import com.shd.codestudio.lang.dom.ParsedFile
import com.shd.codestudio.lang.incremental.DocumentEdit
import com.shd.codestudio.lang.incremental.DocumentSnapshot
import com.shd.codestudio.lang.incremental.IncrementalParser
import com.shd.codestudio.lang.incremental.ReparseResult
import com.shd.codestudio.lang.resolve.ResolveResult
import com.shd.codestudio.lang.resolve.Scope
import com.shd.codestudio.lang.resolve.SymbolFilter
import com.shd.codestudio.lang.resolve.TypeRef
import com.shd.codestudio.platform.Disposable
import com.shd.codestudio.vfs.VirtualFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * [ModuleAnalyzers] is the seam that replaced the host's hardcoded language-to-`ServiceKey` `when`. These
 * cover what that change has to preserve: one analyzer per language, built lazily, disposed with the module,
 * and invalidation reaching every built analyzer without constructing any.
 */
class ModuleAnalyzersTest {

    private class FakeAnalyzer(val language: LanguageId) : SourceAnalyzer, Disposable {
        var disposed = false
        val invalidations = mutableListOf<CacheInvalidation>()

        override fun invalidateCaches(reason: CacheInvalidation) { invalidations += reason }
        override fun dispose() { disposed = true }

        override val incrementalParser = object : IncrementalParser {
            override fun parseFull(snapshot: DocumentSnapshot): ParsedFile = error("not used")
            override fun reparse(
                previous: ParsedFile, newSnapshot: DocumentSnapshot, edits: List<DocumentEdit>
            ): ReparseResult = error("not used")
        }

        override suspend fun parsedFile(file: VirtualFile): ParsedFile = error("not used")
        override suspend fun analyze(file: VirtualFile): AnalysisResult = error("not used")
        override fun resolve(node: DomNode): ResolveResult = ResolveResult.Unresolved
        override fun scopeAt(file: VirtualFile, offset: Int): Scope = object : Scope {
            override val enclosing: Scope? = null
            override fun symbols(filter: SymbolFilter) = emptyList<Nothing>()
            override fun resolve(name: String) = ResolveResult.Unresolved
        }
        override fun expectedTypeAt(file: VirtualFile, offset: Int): TypeRef? = null
    }

    private val java = LanguageId("java")
    private val kotlin = LanguageId("kotlin")

    /** A language the host has never heard of: the case the old hardcoded `when` routed onto Java. */
    private val mylang = LanguageId("mylang")

    @Test
    fun `builds one analyzer per language and caches it`() {
        var built = 0
        val analyzers = ModuleAnalyzers { language -> built++; FakeAnalyzer(language) }

        val first = analyzers.analyzer(java)
        val second = analyzers.analyzer(java)

        assertSame(first, second, "the same language must resolve to the same analyzer")
        assertEquals(1, built, "a cached analyzer must not be rebuilt")
    }

    @Test
    fun `a language the host does not know gets its OWN analyzer, not the Java one`() {
        val analyzers = ModuleAnalyzers { language -> FakeAnalyzer(language) }

        val javaAnalyzer = analyzers.analyzer(java) as FakeAnalyzer
        val custom = analyzers.analyzer(mylang) as FakeAnalyzer

        assertEquals(java, javaAnalyzer.language)
        assertEquals(mylang, custom.language, "a registered backend's language must reach its own analyzer")
        assertTrue(javaAnalyzer !== custom)
    }

    @Test
    fun `nothing is built until it is asked for`() {
        var built = 0
        val analyzers = ModuleAnalyzers { language -> built++; FakeAnalyzer(language) }

        assertEquals(0, built)
        assertNull(analyzers.peek(java), "peek must never construct")
        assertEquals(0, built)

        analyzers.analyzer(java)
        assertEquals(1, built)
        assertTrue(analyzers.peek(java) != null)
    }

    @Test
    fun `invalidation reaches every built analyzer and constructs none`() {
        var built = 0
        val analyzers = ModuleAnalyzers { language -> built++; FakeAnalyzer(language) }
        val a = analyzers.analyzer(java) as FakeAnalyzer
        val b = analyzers.analyzer(kotlin) as FakeAnalyzer
        built = 0

        analyzers.invalidateCaches(CacheInvalidation.SYNTHETIC_CLASSES)

        assertEquals(listOf(CacheInvalidation.SYNTHETIC_CLASSES), a.invalidations)
        assertEquals(listOf(CacheInvalidation.SYNTHETIC_CLASSES), b.invalidations)
        assertEquals(0, built, "invalidation must not build an analyzer for an unopened language")
    }

    @Test
    fun `a throwing analyzer does not stop the others being invalidated`() {
        val analyzers = ModuleAnalyzers { language ->
            if (language == java) object : SourceAnalyzer by FakeAnalyzer(language) {
                override fun invalidateCaches(reason: CacheInvalidation) = error("boom")
            } else FakeAnalyzer(language)
        }
        analyzers.analyzer(java)
        val ok = analyzers.analyzer(kotlin) as FakeAnalyzer

        analyzers.invalidateCaches(CacheInvalidation.BINDINGS)

        assertEquals(listOf(CacheInvalidation.BINDINGS), ok.invalidations)
    }

    @Test
    fun `dispose tears down every built analyzer`() {
        val analyzers = ModuleAnalyzers { language -> FakeAnalyzer(language) }
        val a = analyzers.analyzer(java) as FakeAnalyzer
        val b = analyzers.analyzer(kotlin) as FakeAnalyzer

        analyzers.dispose()

        assertTrue(a.disposed)
        assertTrue(b.disposed)
        assertTrue(analyzers.live().isEmpty())
    }
}
