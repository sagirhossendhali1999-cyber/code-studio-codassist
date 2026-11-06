package com.shd.codestudio.lang.kotlin.index

import com.shd.codestudio.index.IndexExtension
import com.shd.codestudio.index.IndexId
import com.shd.codestudio.index.IndexInput
import com.shd.codestudio.index.IndexOrigin
import com.shd.codestudio.index.InputFilter
import com.shd.codestudio.index.KeyDescriptor
import com.shd.codestudio.index.MatchingMode
import com.shd.codestudio.index.StringKeyDescriptor
import com.shd.codestudio.lang.kotlin.symbols.BuiltinsReader
import com.shd.codestudio.lang.kotlin.symbols.TypeShape

/**
 * `kotlin.builtins` — the owner-keyed shape index for Kotlin's INTRINSIC types (`List`, `Int`, `String`,
 * `Collection`, `Map`, …). These live in `kotlin-stdlib.jar` as `.kotlin_builtins` protobuf fragments, NOT as
 * `.class` files, so [KotlinTypeShapeIndex] (which filters on `.class`) can't carry them. This index decodes
 * each fragment via [BuiltinsReader.shapesFrom] (companion members merged in as statics) and persists one
 * [TypeShape] per type, keyed by its Kotlin FQN (`kotlin.collections.List`) — so the Kotlin backend reads the
 * real Kotlin shape (a read-only `List` has no `add`/`remove`; `Int.` shows `MAX_VALUE`) from the index, with
 * no live jar read, exactly like every other classpath shape. Reuses [TypeShapeExternalizer].
 *
 * Kept SEPARATE from `kotlin.typeShape` because the keys differ: a built-in is queried by its Kotlin FQN,
 * while `kotlin.typeShape` routes a Kotlin type through its JVM mapping (`kotlin.collections.List` →
 * `java.util.List`) — the whole point of built-ins is to NOT use that java.* approximation.
 */
object KotlinBuiltinsIndex : IndexExtension<String, TypeShape> {
    override val id = IndexId("kotlin.builtins")
    // Base 5 folds in TypeShapeExternalizer.FORMAT (the shared codec). Bumped 4→5 to abandon stale on-device
    // segments: the shared externalizer gained isDeprecated + isInfix while this index was left at 4, so an old
    // v4 segment was read with the newer format and desynced mid-value (UTFDataFormatException in readSymbol).
    // 5→6: the decode gained enum ENTRIES, so a segment built before it holds enum shapes with no constants.
    override val version = 6 + TypeShapeExternalizer.FORMAT
    override val keyDescriptor: KeyDescriptor<String> = StringKeyDescriptor
    override val valueExternalizer = TypeShapeExternalizer
    override val matching = MatchingMode.PREFIX_ONLY // queried only by exact Kotlin FQN
    override val inputFilter = InputFilter {
        (it.origin == IndexOrigin.SDK || it.origin == IndexOrigin.LIBRARY) && it.unitName?.endsWith(".kotlin_builtins") == true
    }

    override fun index(input: IndexInput): Map<String, Collection<TypeShape>> {
        val bytes = runCatching { input.bytes() }.getOrNull() ?: return emptyMap()
        return BuiltinsReader.shapesFrom(bytes).mapValues { (_, shape) -> listOf(shape) }
    }
}
