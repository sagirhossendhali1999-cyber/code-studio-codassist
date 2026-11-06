package com.shd.codestudio.lang.kotlin

import com.shd.codestudio.lang.kotlin.interp.Binding
import com.shd.codestudio.lang.kotlin.interp.KotlinTreeResolver
import com.shd.codestudio.lang.kotlin.interp.RNode
import com.shd.codestudio.lang.kotlin.interp.walk
import com.shd.codestudio.lang.kotlin.parse.KotlinParsedFile
import com.shd.codestudio.lang.kotlin.parse.KotlinParserHost
import com.shd.codestudio.lang.kotlin.symbols.KotlinSymbolService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * An EXPLICITLY spelled companion qualifier (`Fill.Companion.NonZero`) must lower to a reference to the
 * companion SINGLETON, so the trailing selector reads a member of it. A bare type name already denotes its
 * companion when used as a value, so lowering the receiver as a value makes the `Companion` selector a
 * property read of the companion it already is, and the preview dies at eval with "no readable property
 * `Companion` on …$Companion". That is the shape a generated icon uses
 * (`pathFillType = PathFillType.Companion.NonZero`), where it empties the whole `path { }`.
 */
class CompanionQualifierLoweringTest {

    private fun lowerFirstFn(code: String): com.shd.codestudio.lang.kotlin.interp.ResolvedFunction {
        val dir = tempProject(mapOf("Main.kt" to code))
        val service = KotlinSymbolService(sourceRoots = listOf(DiskFile(dir)), classpathJars = emptyList())
        val kt = KotlinParserHost.parse("Main.kt", code)
        val parsed = KotlinParsedFile(kt, DiskFile(dir.resolve("Main.kt")), 0)
        return assertNotNull(KotlinTreeResolver(kt, parsed, service).lowerFirstFunction())
    }

    @Test
    fun explicitCompanionQualifierLowersToTheCompanionSingleton() {
        val fn = lowerFirstFn(
            "package demo\n" +
                "class Fill { companion object { val NonZero = 1 } }\n" +
                "fun f() { val x = Fill.Companion.NonZero }\n"
        )
        assertTrue(fn.isComplete, "`Fill.Companion.NonZero` must lower cleanly; diags=${fn.diagnostics}")
        var read: RNode.PropertyGet? = null
        fn.body.walk { if (it is RNode.PropertyGet && it.binding.name == "NonZero") read = it }
        val get = assertNotNull(read, "`NonZero` must lower to a property read")
        val recv = get.receiver
        assertTrue(recv is RNode.Name, "the receiver must be a singleton reference, was ${recv?.let { it::class.simpleName }}")
        val binding = (recv as RNode.Name).binding
        assertTrue(binding is Binding.ObjectRef, "the receiver must bind an object, was ${binding::class.simpleName}")
        assertEquals("demo.Fill.Companion", (binding as Binding.ObjectRef).fqn)
    }

    @Test
    fun aNamedCompanionQualifierLowersToTheSameSingleton() {
        val fn = lowerFirstFn(
            "package demo\n" +
                "class Key { companion object Factory { val Default = 1 } }\n" +
                "fun f() { val x = Key.Factory.Default }\n"
        )
        assertTrue(fn.isComplete, "`Key.Factory.Default` must lower cleanly; diags=${fn.diagnostics}")
        var read: RNode.PropertyGet? = null
        fn.body.walk { if (it is RNode.PropertyGet && it.binding.name == "Default") read = it }
        val recv = assertNotNull(read, "`Default` must lower to a property read").receiver
        val binding = assertNotNull(recv as? RNode.Name, "the receiver must be a singleton reference").binding
        assertEquals("demo.Key.Factory", assertNotNull(binding as? Binding.ObjectRef).fqn)
    }

    /** A NESTED TYPE through the same outer stays a type reference — the singleton branch must not swallow it. */
    @Test
    fun aNestedTypeStillLowersToTheType() {
        val fn = lowerFirstFn(
            "package demo\n" +
                "class Outer { class Inner(val n: Int) }\n" +
                "fun f() { val x = Outer.Inner(2) }\n"
        )
        assertTrue(fn.isComplete, "`Outer.Inner(2)` must lower cleanly; diags=${fn.diagnostics}")
    }
}
