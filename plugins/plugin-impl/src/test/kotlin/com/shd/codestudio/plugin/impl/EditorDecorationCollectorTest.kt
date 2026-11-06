package com.shd.codestudio.plugin.impl

import com.shd.codestudio.platform.PluginId
import com.shd.codestudio.platform.impl.ExtensionRegistryImpl
import com.shd.codestudio.plugin.editor.DecorationStyle
import com.shd.codestudio.plugin.editor.DecorationTint
import com.shd.codestudio.plugin.editor.EDITOR_DECORATION_EP
import com.shd.codestudio.plugin.editor.EditorDecorationContext
import com.shd.codestudio.plugin.editor.EditorDecorationProvider
import com.shd.codestudio.plugin.editor.EditorDecorations
import com.shd.codestudio.plugin.editor.EditorInlay
import com.shd.codestudio.plugin.editor.GutterMark
import com.shd.codestudio.plugin.editor.TextDecoration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private val PLUGIN_ID = PluginId("decorations-test")

private class TestContext(
    override val path: String = "App.kt",
    override val text: String = "line one\nline two\n",
    override val languageId: String? = "kotlin",
) : EditorDecorationContext

private class FakeProvider(
    override val id: String,
    private val result: EditorDecorations = EditorDecorations.EMPTY,
    private val applies: Boolean = true,
    private val throwOnApplies: Boolean = false,
    private val throwOnDecorate: Boolean = false,
) : EditorDecorationProvider {
    var decorateCalls = 0
        private set

    override fun appliesTo(ctx: EditorDecorationContext): Boolean {
        if (throwOnApplies) error("appliesTo blew up")
        return applies
    }

    override suspend fun decorate(ctx: EditorDecorationContext): EditorDecorations {
        decorateCalls++
        if (throwOnDecorate) error("decorate blew up")
        return result
    }
}

private fun decoration(start: Int, end: Int, order: Int = 0) =
    TextDecoration(start, end, DecorationStyle.Background, DecorationTint.Success, order = order)

class EditorDecorationCollectorTest {

    @Test
    fun collectsEveryApplicableProviderInRegistrationOrder() {
        val registry = ExtensionRegistryImpl()
        registry.register(
            EDITOR_DECORATION_EP,
            FakeProvider("coverage", EditorDecorations(ranges = listOf(decoration(0, 4)))),
            PLUGIN_ID,
        )
        registry.register(
            EDITOR_DECORATION_EP,
            FakeProvider("vcs", EditorDecorations(gutter = listOf(GutterMark(1, "plus")))),
            PLUGIN_ID,
        )
        val result = runBlocking { EditorDecorationCollector(registry).collect(TestContext()) }

        assertEquals(1, result.ranges.size)
        assertEquals(1, result.gutter.size)
        assertEquals(1, result.gutter.single().line)
    }

    @Test
    fun aProviderThatDoesNotApplyIsNeverAsked() {
        val registry = ExtensionRegistryImpl()
        val skipped = FakeProvider("skipped", applies = false)
        registry.register(EDITOR_DECORATION_EP, skipped, PLUGIN_ID)
        val collector = EditorDecorationCollector(registry)

        assertFalse(collector.anyApplies(TestContext()))
        runBlocking { collector.collect(TestContext()) }
        assertEquals(0, skipped.decorateCalls, "appliesTo gates decorate")
    }

    @Test
    fun oneFailingProviderDoesNotCostTheOthers() {
        val registry = ExtensionRegistryImpl()
        registry.register(EDITOR_DECORATION_EP, FakeProvider("gate-thrower", throwOnApplies = true), PLUGIN_ID)
        registry.register(EDITOR_DECORATION_EP, FakeProvider("body-thrower", throwOnDecorate = true), PLUGIN_ID)
        registry.register(
            EDITOR_DECORATION_EP,
            FakeProvider("good", EditorDecorations(ranges = listOf(decoration(0, 4)))),
            PLUGIN_ID,
        )
        val failed = ArrayList<String>()
        val result = runBlocking {
            EditorDecorationCollector(registry).collect(TestContext()) { id, _ -> failed += id }
        }

        assertEquals(listOf("gate-thrower", "body-thrower"), failed, "each failure is attributed to its provider")
        assertEquals(1, result.ranges.size, "the working provider's marks still arrive")
    }

    @Test
    fun cancellationPropagatesRatherThanBeingSwallowed() {
        val registry = ExtensionRegistryImpl()
        registry.register(
            EDITOR_DECORATION_EP,
            object : EditorDecorationProvider {
                override val id = "slow"
                override suspend fun decorate(ctx: EditorDecorationContext): EditorDecorations {
                    while (true) yield()
                }
            },
            PLUGIN_ID,
        )
        val failed = ArrayList<String>()
        val job = Job()
        runBlocking {
            val run = CoroutineScope(job).launch {
                EditorDecorationCollector(registry).collect(TestContext()) { id, _ -> failed += id }
            }
            yield()
            run.cancel()
            run.join()
            assertTrue(run.isCancelled, "the collect call is cancelled with its caller")
        }
        assertTrue(failed.isEmpty(), "a cancellation is not reported as a provider failure")
    }

    @Test
    fun marksOutsideTheTextAreDropped() {
        val registry = ExtensionRegistryImpl()
        val text = "one\ntwo\n" // 8 chars, 3 lines (the trailing break opens an empty last line)
        registry.register(
            EDITOR_DECORATION_EP,
            FakeProvider(
                "stale",
                EditorDecorations(
                    ranges = listOf(
                        decoration(0, 3),      // valid
                        decoration(4, 400),    // end past the buffer
                        decoration(5, 5),      // empty range
                        decoration(6, 2),      // inverted
                    ),
                    gutter = listOf(
                        GutterMark(2, "dot"),  // the empty last line is addressable
                        GutterMark(3, "dot"),  // one past the end
                        GutterMark(-1, "dot"),
                    ),
                    inlays = listOf(
                        EditorInlay(8, ": Int"),   // end of buffer is a legal anchor
                        EditorInlay(9, ": Int"),
                    ),
                ),
            ),
            PLUGIN_ID,
        )
        val result = runBlocking {
            EditorDecorationCollector(registry).collect(TestContext(text = text))
        }

        assertEquals(listOf(0 to 3), result.ranges.map { it.startOffset to it.endOffset })
        assertEquals(listOf(2), result.gutter.map { it.line })
        assertEquals(listOf(8), result.inlays.map { it.offset })
    }

    @Test
    fun rangesAreOrderedSoTheHighestOrderDrawsLast() {
        val registry = ExtensionRegistryImpl()
        registry.register(
            EDITOR_DECORATION_EP,
            FakeProvider(
                "layered",
                EditorDecorations(ranges = listOf(decoration(0, 4, order = 5), decoration(0, 4, order = -1))),
            ),
            PLUGIN_ID,
        )
        val result = runBlocking { EditorDecorationCollector(registry).collect(TestContext()) }

        assertEquals(listOf(-1, 5), result.ranges.map { it.order })
    }

    @Test
    fun noProvidersIsTheEmptyResultWithoutAllocating() {
        val collector = EditorDecorationCollector(ExtensionRegistryImpl())
        val result = runBlocking { collector.collect(TestContext()) }
        assertTrue(result.isEmpty)
        assertFalse(collector.anyApplies(TestContext()))
    }
}
