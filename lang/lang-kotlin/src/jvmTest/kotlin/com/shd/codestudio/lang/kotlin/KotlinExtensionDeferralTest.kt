package com.shd.codestudio.lang.kotlin

import com.shd.codestudio.index.Hit
import com.shd.codestudio.index.IndexId
import com.shd.codestudio.index.IndexScope
import com.shd.codestudio.index.IndexService
import com.shd.codestudio.index.IndexStatus
import com.shd.codestudio.lang.kotlin.index.CallableShape
import com.shd.codestudio.lang.kotlin.index.KotlinCallableIndex
import com.shd.codestudio.lang.kotlin.interp.KotlinTreeResolver
import com.shd.codestudio.lang.kotlin.parse.KotlinParsedFile
import com.shd.codestudio.lang.kotlin.parse.KotlinParserHost
import com.shd.codestudio.lang.kotlin.symbols.KotlinSymbolService
import com.shd.codestudio.lang.kotlin.symbols.KotlinType
import com.shd.codestudio.lang.resolve.SymbolKind
import com.shd.codestudio.platform.Disposable
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The reported preview failure: `OrderViewModel` reached
 * "unresolved/ambiguous call `contains` (candidates=9, recv=kotlin.String)" / "`isBlank`". A stdlib String
 * extension (`recv.contains(x)`, `recv.isBlank()`) whose overloads the parse-only resolver can't narrow
 * statically was rejected outright, marking the whole class incomplete so the preview refused to render — even
 * though every candidate lives on the ONE `kotlin.text.StringsKt` facade and the runtime dispatcher re-resolves
 * it by the actual argument values. [KotlinTreeResolver.deferToRuntimeMember] now defers such a homogeneous
 * binary-extension set to the runtime instead of failing the lower.
 *
 * Reproduced with a two-overload extension set on one facade that a `String` argument binds to BOTH (so no
 * most-specific candidate exists → the static tie-break gives up), mirroring the un-narrowable `StringsKt`
 * shape.
 */
class KotlinExtensionDeferralTest {

    // Modeled on the real stdlib String extensions: in the default-imported `kotlin.text` package, on the one
    // `kotlin.text.StringsKt` facade (so it's in scope and reaches the overload tie-break, like `contains`).
    private fun ext(paramFqn: String) = CallableShape(
        name = "zap", kind = SymbolKind.METHOD, receiverFqn = "kotlin.CharSequence",
        signature = "(x: $paramFqn)", packageName = "kotlin.text", receiverTypeParam = null, typeParameters = emptyList(),
        returnType = KotlinType("kotlin.Boolean"), paramTypes = listOf(KotlinType(paramFqn)),
        receiverTypeArgs = emptyList(), declaringClassFqn = "kotlin.text.StringsKt", paramNames = listOf("x"),
        isComposable = false, isInline = false, isInfix = false, isSuspend = false,
    )

    @Test
    fun ambiguousLibraryExtensionSetDefersInsteadOfFailingTheLower() {
        // Two `zap` extensions on the ONE `demo.StrKt` facade; a String argument is a subtype of both parameter
        // types (`CharSequence`, `Any`), so the static resolver can't pick one — the `StringsKt.contains` shape.
        val shapes = listOf(ext("kotlin.CharSequence"), ext("kotlin.Any"))
        val key = KotlinCallableIndex.extPrefix("kotlin.CharSequence", "zap")
        @Suppress("UNCHECKED_CAST")
        val idx = object : IndexService {
            override fun <V : Any> exact(id: IndexId, k: String): Sequence<V> =
                if (id == KotlinCallableIndex.id && k == key) shapes.asSequence().map { it as V } else emptySequence()
            override fun <V : Any> prefix(id: IndexId, prefix: String, limit: Int): Sequence<Hit<V>> =
                if (id == KotlinCallableIndex.id && key.startsWith(prefix)) shapes.asSequence().map { Hit(key, it as V, 0) } else emptySequence()
            override fun <V : Any> fuzzy(id: IndexId, pattern: String, limit: Int): Sequence<Hit<V>> = emptySequence()
            override suspend fun ensureUpToDate(scope: IndexScope) {}
            override suspend fun reindexSource(path: Path, text: String) {}
            override val status = IndexStatus(ready = true)
            override fun observeStatus(listener: (IndexStatus) -> Unit) = Disposable { }
        }
        val service = KotlinSymbolService(sourceRoots = emptyList(), classpathJars = listOf(stdlibJarPath().toString()), index = idx)
        val code = "fun f(s: String): Boolean = s.zap(s)"
        val kt = KotlinParserHost.parse("Use.kt", code)
        val fn = KotlinTreeResolver(kt, KotlinParsedFile(kt, FakeFile("Use.kt"), 0), service).lowerFirstFunction()
        assertTrue(
            fn != null && fn.isComplete,
            "an un-narrowable library-extension overload set on one facade must defer to the runtime, not fail the lower; diags=${fn?.diagnostics?.map { it.reason }}",
        )
    }
}
