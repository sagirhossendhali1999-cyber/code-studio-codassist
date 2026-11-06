package com.shd.codestudio.preview.impl

import com.shd.codestudio.preview.AttrReader
import com.shd.codestudio.preview.PlaceholderRenderer
import com.shd.codestudio.preview.RCanvas
import com.shd.codestudio.preview.RenderContext
import com.shd.codestudio.preview.RenderNode
import com.shd.codestudio.preview.Renderer
import com.shd.codestudio.preview.Size

/**
 * Platform seam that instantiates a user custom view into a [RenderNode] whose [RenderNode.host] runs the
 * user's `onMeasure`/`onLayout`/`onDraw`. The device impl loads the instrumented, dexed class through a
 * `DexClassLoader` and constructs it against a `BridgeContext`/`BridgeAttributeSet`; the desktop impl loads
 * the shim-compiled class through a `URLClassLoader`. Returns null when it can't instantiate (missing deps,
 * a throwing constructor, no runtime configured) → the inflater falls back to a placeholder.
 */
interface CustomViewFactory {
    fun create(fqName: String, attrs: AttrReader, ctx: RenderContext): RenderNode?

    companion object {
        /** No custom-view runtime: every custom view renders as a placeholder. */
        val NONE: CustomViewFactory = object : CustomViewFactory {
            override fun create(fqName: String, attrs: AttrReader, ctx: RenderContext): RenderNode? = null
        }
    }
}

/**
 * Drives a custom view through its owned base ([com.shd.codestudio.preview.ViewHost]): the user's overridden
 * `onMeasure/onLayout/onDraw` run against *our* specs and *our* canvas. Any exception in user code degrades
 * to a placeholder for that node instead of blanking the tree (§12). Falls back to the placeholder if the
 * node somehow has no host.
 */
object CustomViewRenderer : Renderer {

    override fun measure(node: RenderNode, widthSpec: Int, heightSpec: Int, ctx: RenderContext): Size {
        val host = node.host ?: return PlaceholderRenderer.measure(node, widthSpec, heightSpec, ctx)
        return runCatching {
            host.measure(widthSpec, heightSpec)
            Size(host.measuredWidth, host.measuredHeight)
        }.getOrElse { PlaceholderRenderer.measure(node, widthSpec, heightSpec, ctx) }.also { node.measured = it }
    }

    override fun layout(node: RenderNode, l: Int, t: Int, r: Int, b: Int, ctx: RenderContext) {
        node.setBounds(l, t, r, b)
        runCatching { node.host?.layout(l, t, r, b) }
    }

    override fun draw(node: RenderNode, canvas: RCanvas, ctx: RenderContext) {
        drawBackground(node, canvas, ctx)
        val host = node.host ?: return PlaceholderRenderer.draw(node, canvas, ctx)
        runCatching {
            canvas.save()
            canvas.translate(node.left.toFloat(), node.top.toFloat())
            host.drawContent(canvas)
            canvas.restore()
        }.onFailure {
            canvas.restore()
            PlaceholderRenderer.draw(node, canvas, ctx)
        }
    }
}
