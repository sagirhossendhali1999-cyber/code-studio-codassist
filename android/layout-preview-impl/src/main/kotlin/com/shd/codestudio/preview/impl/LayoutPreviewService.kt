package com.shd.codestudio.preview.impl

import com.shd.codestudio.android.support.resources.ResourceRepository
import com.shd.codestudio.preview.LayoutPreviewResult
import com.shd.codestudio.preview.RImage
import com.shd.codestudio.preview.RendererRegistry
import com.shd.codestudio.preview.SimpleRenderContext
import com.shd.codestudio.preview.impl.headless.HeadlessGraphics

/**
 * Builds the [LayoutPreviewResult] the UI renders: inflate the layout XML into the owned tree (resolving
 * resources, instantiating custom views via the [customViewFactory]) and wrap it in the activity's system
 * chrome. The returned tree is *re-measured* by the UI with its own platform [com.shd.codestudio.preview.RGraphics]
 * (real text metrics), so the [HeadlessGraphics] used here only services inflation, never final layout.
 */
class LayoutPreviewService(
    private val customViewFactory: CustomViewFactory = CustomViewFactory.NONE,
    private val registry: RendererRegistry = DefaultRenderers.registry(),
) {
    fun preview(
        xml: String,
        repo: ResourceRepository,
        themeName: String?,
        title: String,
        density: Float = 2f,
        scaledDensity: Float = density,
        showChrome: Boolean = true,
        night: Boolean = false,
        imageLoader: (resType: String, name: String, file: String?) -> RImage? = { _, _, _ -> null },
        layoutProvider: (name: String) -> String? = { null },
    ): LayoutPreviewResult {
        val resources = ProjectPreviewResources(repo, density, scaledDensity, imageLoader, night, themeName)
        val ctx = SimpleRenderContext(HeadlessGraphics(), resources, density, scaledDensity)
        val inflater = LayoutInflater(registry, customViewFactory, layoutProvider)
        val content = inflater.inflate(xml, ctx)
        val root = if (showChrome) {
            SystemChrome.wrap(content, PreviewChrome.fromTheme(repo, resources, themeName, title), ctx)
        } else {
            content
        }
        return LayoutPreviewResult(root, resources, density, scaledDensity, imageFile = { resources.imageFilePath(it) }, problems = inflater.problems.toList())
    }
}
