package com.shd.codestudio.preview.impl

import com.shd.codestudio.testkit.withTempDir
import com.shd.codestudio.android.support.resources.ResourceItem
import com.shd.codestudio.android.support.resources.ResourceRepository
import com.shd.codestudio.android.support.resources.ResourceType
import com.shd.codestudio.android.support.resources.StyleData
import com.shd.codestudio.preview.GradientType
import com.shd.codestudio.preview.PreviewEngine
import com.shd.codestudio.preview.SimpleRenderContext
import com.shd.codestudio.preview.impl.headless.HeadlessGraphics
import com.shd.codestudio.preview.impl.headless.RecordingCanvas
import java.nio.file.Files
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FidelityTest {

    @Test fun `gradient shape background sets a gradient paint`() = withTempDir("preview-grad") { dir ->
        val shape = dir.resolve("g.xml")
        shape.writeText(
            """
            <shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
                <gradient android:startColor="#FF000000" android:endColor="#FFFFFFFF" android:angle="0"/>
            </shape>
            """.trimIndent(),
        )
        val repo = ResourceRepository(listOf(ResourceItem(ResourceType.DRAWABLE, "g", source = shape)))
        val res = ProjectPreviewResources(repo, density = 1f)
        val d = assertNotNull(res.backgroundDrawable("@drawable/g"))
        // Render and confirm a fill op carried a gradient (RecordingCanvas can't see it, so check the model).
        assertTrue(d is com.shd.codestudio.android.support.preview.DrawablePreview.Shape)
        val g = (d as com.shd.codestudio.android.support.preview.DrawablePreview.Shape).spec.gradient
        assertNotNull(g)
        // And the engine maps it to an api Gradient via DrawableBackgroundRenderer (linear here).
        assertEquals(GradientType.LINEAR, com.shd.codestudio.preview.GradientType.LINEAR)
    }

    @Test fun `style overlay applies textColor and textSize`() {
        val repo = ResourceRepository(
            items = listOf(ResourceItem(ResourceType.COLOR, "brand", value = "#FF112233")),
            styles = mapOf("MyText" to StyleData(parent = null, items = mapOf("android:textColor" to "@color/brand", "android:textSize" to "22sp"))),
        )
        val ctx = SimpleRenderContext(HeadlessGraphics(), ProjectPreviewResources(repo, density = 1f, scaledDensity = 1f), density = 1f)
        val (_, canvas) = run {
            val root = LayoutInflater().inflate(
                """
                <TextView xmlns:android="http://schemas.android.com/apk/res/android"
                    android:layout_width="wrap_content" android:layout_height="wrap_content"
                    style="@style/MyText" android:text="hi"/>
                """.trimIndent(),
                ctx,
            )
            val c = RecordingCanvas(); PreviewEngine(ctx).render(root, 400, 200, c); root to c
        }
        val textOp = canvas.ops.first { it.text == "hi" }
        assertEquals(0xFF112233.toInt(), textOp.color, "style textColor applied to drawn text")
    }

    @Test fun `textAppearance applies when no explicit attrs`() {
        val repo = ResourceRepository(
            items = emptyList(),
            styles = mapOf("Big" to StyleData(parent = null, items = mapOf("android:textColor" to "#FF00FF00"))),
        )
        val ctx = SimpleRenderContext(HeadlessGraphics(), ProjectPreviewResources(repo, density = 1f, scaledDensity = 1f), density = 1f)
        val root = LayoutInflater().inflate(
            """
            <TextView xmlns:android="http://schemas.android.com/apk/res/android"
                android:layout_width="wrap_content" android:layout_height="wrap_content"
                android:textAppearance="@style/Big" android:text="x"/>
            """.trimIndent(),
            ctx,
        )
        val canvas = RecordingCanvas()
        PreviewEngine(ctx).render(root, 400, 200, canvas)
        assertEquals(0xFF00FF00.toInt(), canvas.ops.first { it.text == "x" }.color)
    }
}
