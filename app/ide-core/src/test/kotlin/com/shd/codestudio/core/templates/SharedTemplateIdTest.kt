package com.shd.codestudio.core.templates

import com.shd.codestudio.core.ApplicationEnvironment
import com.shd.codestudio.model.ProjectTemplateRegistry
import com.shd.codestudio.model.template.ProjectScaffold
import com.shd.codestudio.model.template.ProjectTemplate
import com.shd.codestudio.model.template.ProjectTemplateExtensionPoint
import com.shd.codestudio.model.template.TemplateArgs
import com.shd.codestudio.model.template.TemplateCategory
import com.shd.codestudio.model.template.TemplateId
import com.shd.codestudio.model.template.TemplateParameter
import com.shd.codestudio.platform.PluginId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/**
 * Two plugins contributing a template under one id: the published Dart plugin's `dart-console`, and a Dart plugin
 * someone was developing with an id of its own and the same template id. Both loaded, the Create-Project gallery
 * got the id twice, and its grid (keyed on template ids) crashed the app.
 */
class SharedTemplateIdTest {

    private class Template(id: String, override val displayName: String) : ProjectTemplate {
        override val id = TemplateId(id)
        override val description = ""
        override val category = TemplateCategory.OTHER
        override val iconId = ""
        override fun parameters(): List<TemplateParameter> = emptyList()
        override fun generate(scaffold: ProjectScaffold, args: TemplateArgs) = Unit
    }

    @Test
    fun aTemplateIdIsListedOnceAndTheFirstRegisteredWins() {
        ApplicationEnvironment().use { env ->
            val published = Template("dart-console", "Dart Console App")
            val inDevelopment = Template("dart-console", "My Dart Console")
            env.platform.extensions.register(ProjectTemplateExtensionPoint, published, PluginId("dev.codestudio.dart"))
            env.platform.extensions.register(ProjectTemplateExtensionPoint, inDevelopment, PluginId("com.example.mydart"))

            val registry = ProjectTemplateRegistry(env.platform.extensions)
            val dart = registry.all().filter { it.id.value == "dart-console" }
            assertEquals(1, dart.size)
            assertSame(published, dart.single())
            assertSame(published, registry.byId(TemplateId("dart-console")), "the gallery and creation agree")
            assertEquals(registry.all().size, registry.all().map { it.id }.distinct().size, "no id twice")
        }
    }
}
