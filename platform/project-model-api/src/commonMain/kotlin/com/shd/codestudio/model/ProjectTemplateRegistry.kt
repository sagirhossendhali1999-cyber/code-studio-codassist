package com.shd.codestudio.model

import com.shd.codestudio.model.template.ProjectTemplate
import com.shd.codestudio.model.template.ProjectTemplateExtensionPoint
import com.shd.codestudio.model.template.TemplateId
import com.shd.codestudio.platform.ExtensionRegistry
import com.shd.codestudio.platform.PluginId

/**
 * Resolves the [ProjectTemplate]s plugins contributed to [ProjectTemplateExtensionPoint]: the
 * Create-Project gallery's source of truth. Mirrors [ModuleTypeRegistry] over its own extension point.
 *
 * One template per id, the first registered (built-ins load before installed plugins). Two plugins can both
 * contribute an id, a plugin in development reusing one a published plugin already ships, say, and the gallery
 * keyed on ids crashed on the second. The host reports the one left out against its plugin.
 */
class ProjectTemplateRegistry(private val extensions: ExtensionRegistry) {
    fun register(template: ProjectTemplate, plugin: PluginId) =
        extensions.register(ProjectTemplateExtensionPoint, template, plugin)

    fun all(): List<ProjectTemplate> = extensions.extensions(ProjectTemplateExtensionPoint).distinctBy { it.id }

    fun byId(id: TemplateId): ProjectTemplate? = all().firstOrNull { it.id == id }
}
