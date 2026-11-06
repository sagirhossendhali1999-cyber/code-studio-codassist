package com.shd.codestudio.lang.kotlin

import com.shd.codestudio.index.IndexScope
import com.shd.codestudio.index.impl.IndexServiceImpl
import com.shd.codestudio.lang.kotlin.index.KotlinCallableIndex
import com.shd.codestudio.lang.kotlin.index.KotlinTypeShapeIndex
import com.shd.codestudio.lang.kotlin.interp.DispatchKind
import com.shd.codestudio.lang.kotlin.interp.KotlinTreeResolver
import com.shd.codestudio.lang.kotlin.interp.RNode
import com.shd.codestudio.lang.kotlin.interp.walk
import com.shd.codestudio.lang.kotlin.parse.KotlinParsedFile
import com.shd.codestudio.lang.kotlin.parse.KotlinParserHost
import com.shd.codestudio.lang.kotlin.symbols.KotlinSymbolService
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Probe for the Compose CANVAS/DRAW path (the `Canvas { drawRect(...) }` shape) through the BINARY (`@Metadata`)
 * lowering — the device path. A draw block differs from the layout content slots the other fakes cover: its
 * receiver lambda is NON-`@Composable` and its members take INLINE VALUE-CLASS params (name-mangled on the JVM).
 * This isolates whether the RESOLVER can lower those member calls (a headlessly-verifiable layer) — distinct
 * from the reflective DISPATCH and the device-only rasterization the draw phase performs.
 */
class CanvasDrawScopeLoweringTest {

    private val CLASSES = listOf(
        "androidx/compose/runtime/Composable.class",
        "com/shd/codestudio/fakecompose/FakeModifier.class",
        "com/shd/codestudio/fakecompose/FakeModifier\$Companion.class",
        "com/shd/codestudio/fakecompose/DrawColor.class",
        "com/shd/codestudio/fakecompose/DrawColor\$Companion.class",
        "com/shd/codestudio/fakecompose/DrawOffset.class",
        "com/shd/codestudio/fakecompose/DrawOffset\$Companion.class",
        "com/shd/codestudio/fakecompose/DrawSize.class",
        "com/shd/codestudio/fakecompose/DrawSize\$Companion.class",
        "com/shd/codestudio/fakecompose/FakeDrawScope.class",
        "com/shd/codestudio/fakecompose/FakeDrawScopeKt.class",
    )

    private fun jar(): Path {
        val jar = Files.createTempFile("fake-canvas", ".jar")
        ZipOutputStream(Files.newOutputStream(jar)).use { zos ->
            zos.putNextEntry(ZipEntry("META-INF/fakecompose.kotlin_module")); zos.closeEntry()
            for (name in CLASSES) {
                val bytes = javaClass.classLoader.getResourceAsStream(name)?.use { it.readBytes() }
                    ?: error("missing test class on the classpath: $name")
                zos.putNextEntry(ZipEntry(name)); zos.write(bytes); zos.closeEntry()
            }
        }
        return jar
    }

    private val fakeJar = jar()
    private val index = IndexServiceImpl(
        listOf(KotlinTypeShapeIndex, KotlinCallableIndex),
        cacheRoot = Files.createTempDirectory("idx"),
    ).also { runBlocking { it.ensureUpToDate(IndexScope(libraryJars = listOf(fakeJar))) } }
    private val service = KotlinSymbolService(sourceRoots = emptyList(), classpathJars = listOf(fakeJar.toString()), index = index)

    private fun lower(code: String): com.shd.codestudio.lang.kotlin.interp.ResolvedFunction {
        val kt = KotlinParserHost.parse("Use.kt", "import com.shd.codestudio.fakecompose.*\nimport androidx.compose.runtime.*\n$code")
        val parsed = KotlinParsedFile(kt, FakeFile("Use.kt"), 0)
        return KotlinTreeResolver(kt, parsed, service).lowerFirstFunction()!!
    }

    private fun gapsOf(fn: com.shd.codestudio.lang.kotlin.interp.ResolvedFunction): List<String> {
        val gaps = ArrayList<String>()
        fn.body.walk { if (it is RNode.Unsupported) gaps += "${it.reason}: ${it.text}" }
        return gaps
    }

    @Test
    fun theFakeCanvasResolvesFromTheBinaryIndex() {
        assertNotNull(
            service.topLevelByName("FakeCanvas").firstOrNull(),
            "FakeCanvas must resolve from the binary index (fixture sanity)",
        )
    }

    @Test
    fun aDrawRectInsideACanvasLambdaResolvesAsAMemberCall() {
        // Canvas(Modifier) { drawRect(color = Color.Red, size = size) } — the basic Compose Canvas shape:
        // a value-class-param member call, with a named arg + an omitted defaulted arg, inside a NON-composable
        // receiver lambda whose receiver type (FakeDrawScope) is inferred from FakeCanvas's `onDraw` param.
        val fn = lower("@Composable fun C() { FakeCanvas(FakeModifier) { drawRect(color = DrawColor.Red, size = size) } }")
        val gaps = gapsOf(fn)
        assertTrue(gaps.isEmpty(), "the draw block should lower with no gaps; got $gaps")

        var drawRect = 0
        fn.body.walk {
            if (it is RNode.Call && it.callee.displayName == "drawRect") {
                // A plain member on the lambda's implicit receiver: dispatch is MEMBER (not a top-level function),
                // and the receiver is carried either explicitly (`.receiver`) or as the enclosing lambda's `this`
                // (`dispatchReceiver`) — the interpreter binds it from the current receiver frame at run time.
                assertTrue(
                    it.dispatch == DispatchKind.MEMBER || it.dispatch == DispatchKind.MEMBER_EXTENSION,
                    "drawRect should dispatch on the DrawScope receiver, was ${it.dispatch}",
                )
                drawRect++
            }
        }
        assertTrue(drawRect == 1, "the drawRect call should lower once; found $drawRect")
    }

    @Test
    fun drawLineAndDrawCircleResolveToo() {
        val fn = lower(
            "@Composable fun C() { FakeCanvas(FakeModifier) { " +
                "drawLine(DrawColor.Red, DrawOffset.Zero, center)\n" +
                "drawCircle(DrawColor.Black, radius = 4f) } }",
        )
        assertTrue(gapsOf(fn).isEmpty(), "drawLine/drawCircle should lower with no gaps; got ${gapsOf(fn)}")
    }

    @Test
    fun inlineTransformBlocksResolve() {
        // inset(4f) { drawRect(...) } and withTransform({ }) { drawRect(...) } — the transform cases that show
        // "no preview": nested NON-composable draw blocks through an INLINE extension.
        val fn = lower(
            "@Composable fun C() { FakeCanvas(FakeModifier) { " +
                "fakeInset(4f) { drawRect(color = DrawColor.Red, size = size) }\n" +
                "fakeWithTransform({ }) { drawCircle(DrawColor.Black) } } }",
        )
        assertTrue(gapsOf(fn).isEmpty(), "inline transform draw blocks should lower with no gaps; got ${gapsOf(fn)}")
    }
}
