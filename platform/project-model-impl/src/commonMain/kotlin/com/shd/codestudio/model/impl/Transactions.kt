package com.shd.codestudio.model.impl

import com.shd.codestudio.model.BuildSystemId
import com.shd.codestudio.model.ContentRole
import com.shd.codestudio.model.DependencyScope
import com.shd.codestudio.model.Facet
import com.shd.codestudio.model.FacetCodecRegistry
import com.shd.codestudio.model.FacetData
import com.shd.codestudio.model.LanguageLevel
import com.shd.codestudio.model.ModifiableModule
import com.shd.codestudio.model.Module
import com.shd.codestudio.model.ModuleId
import com.shd.codestudio.model.ModuleType
import com.shd.codestudio.model.OrderEntry
import com.shd.codestudio.model.Project
import com.shd.codestudio.model.ProjectId
import com.shd.codestudio.model.ProjectModelTransaction
import com.shd.codestudio.model.RESERVED_FACET_TABLES
import com.shd.codestudio.model.SdkRef
import com.shd.codestudio.model.SourceSetTemplate
import com.shd.codestudio.model.WorkspaceTransaction
import com.shd.codestudio.model.event.DependenciesChanged
import com.shd.codestudio.model.event.FacetsChanged
import com.shd.codestudio.model.event.ModuleAdded
import com.shd.codestudio.model.event.ModuleRemoved
import com.shd.codestudio.model.event.ModuleSettingsChanged
import com.shd.codestudio.model.event.ProjectAdded
import com.shd.codestudio.model.event.ProjectModelEvent
import com.shd.codestudio.model.event.ProjectRemoved
import com.shd.codestudio.model.event.ProjectSettingsChanged
import com.shd.codestudio.model.event.SourceSetsChanged
import com.shd.codestudio.vfs.VirtualFile

/**
 * Workspace-level structural edit: add/remove [Project]s. Changes are staged here and only become
 * visible when [commit] installs a new snapshot atomically and publishes [ProjectAdded]/[ProjectRemoved].
 */
internal class WorkspaceTransactionImpl(private val store: ProjectModelStore) : WorkspaceTransaction {
    private val base = store.data
    private val added = LinkedHashMap<String, ProjectData>()
    private val removed = LinkedHashSet<String>()
    private val rebound = LinkedHashMap<String, String>()
    private var done = false

    override fun addProject(name: String, buildSystem: BuildSystemId, rootDir: VirtualFile): Project {
        require(base.projects.none { it.id == name } && !added.containsKey(name)) { "project '$name' already exists" }
        val pd = ProjectData(
            id = name,
            name = name,
            rootRelPath = store.relativizeToWorkspace(rootDir),
            buildSystemId = buildSystem.value,
        )
        added[name] = pd
        return ProjectImpl(pd, store) // a transient view of the staged project
    }

    override fun removeProject(id: ProjectId) {
        added.remove(id.value)
        removed.add(id.value)
    }

    override fun setBuildSystem(id: ProjectId, buildSystem: BuildSystemId) {
        added[id.value]?.let { added[id.value] = it.copy(buildSystemId = buildSystem.value); return }
        rebound[id.value] = buildSystem.value
    }

    override fun commit() {
        check(!done) { "transaction already committed or disposed" }
        done = true
        val events = ArrayList<ProjectModelEvent>()
        val result = LinkedHashMap<String, ProjectData>()
        for (p in base.projects) result[p.id] = p
        for (id in removed) if (result.remove(id) != null) events.add(ProjectRemoved(ProjectId(id)))
        for ((id, buildSystemId) in rebound) {
            val current = result[id] ?: continue
            if (current.buildSystemId == buildSystemId) continue
            result[id] = current.copy(buildSystemId = buildSystemId)
            events.add(ProjectSettingsChanged(ProjectId(id)))
        }
        for ((id, pd) in added) {
            result[id] = pd
            events.add(ProjectAdded(ProjectId(id)))
        }
        store.commit(base.copy(projects = result.values.toList()), events)
    }

    override fun dispose() {
        done = true
    }
}

/**
 * Project-level structural edit: add/remove/modify [Module]s. Staged through [ModuleBuilder]s; [commit]
 * installs the new snapshot and publishes one event per change (ModuleAdded for new modules, otherwise
 * the specific Dependencies/SourceSets/Facets/ModuleSettings changes).
 */
internal class ProjectModelTransactionImpl(
    private val store: ProjectModelStore,
    private val projectId: ProjectId,
) : ProjectModelTransaction {
    private val project = store.data.projects.first { it.id == projectId.value }
    private val builders = LinkedHashMap<String, ModuleBuilder>()
    private val removed = LinkedHashSet<String>()
    private var done = false

    override fun addModule(name: String, type: ModuleType): ModifiableModule {
        require(builders[name] == null && project.modules.none { it.id == name }) { "module '$name' already exists" }
        val builder = ModuleBuilder(id = name, name = name, dirRelPath = name, typeId = type.id, initial = null, codecs = store.facetCodecs)
        type.defaultSourceSets().forEach { builder.addSourceSet(it) }
        type.defaultFacets().forEach { template ->
            store.facetCodecs.codecFor(template.key)?.let { codec ->
                builder.putFacetData(FacetData(codec.tomlTable, template.defaults))
            }
        }
        builders[name] = builder
        return builder
    }

    override fun removeModule(id: ModuleId) {
        builders.remove(id.value)
        removed.add(id.value)
    }

    override fun module(id: ModuleId): ModifiableModule = builders.getOrPut(id.value) {
        val existing = project.modules.first { it.id == id.value }
        ModuleBuilder(existing.id, existing.name, existing.dirRelPath, existing.typeId, existing, store.facetCodecs)
    }

    override fun commit() {
        check(!done) { "transaction already committed or disposed" }
        done = true
        val events = ArrayList<ProjectModelEvent>()
        val result = LinkedHashMap<String, ModuleData>()
        for (m in project.modules) result[m.id] = m
        for (id in removed) if (result.remove(id) != null) events.add(ModuleRemoved(projectId, ModuleId(id)))
        for ((id, b) in builders) {
            result[id] = b.toData()
            if (b.isNew) {
                events.add(ModuleAdded(projectId, ModuleId(id)))
            } else {
                if (b.depsChanged) events.add(DependenciesChanged(projectId, ModuleId(id)))
                if (b.sourceSetsChanged) events.add(SourceSetsChanged(projectId, ModuleId(id)))
                if (b.facetsChanged) events.add(FacetsChanged(projectId, ModuleId(id)))
                if (b.settingsChanged) events.add(ModuleSettingsChanged(projectId, ModuleId(id)))
            }
        }
        val newProject = project.copy(modules = result.values.toList())
        val newWs = store.data.copy(projects = store.data.projects.map { if (it.id == projectId.value) newProject else it })
        store.commit(newWs, events)
    }

    override fun dispose() {
        done = true
    }
}

internal class ModuleBuilder(
    private val id: String,
    private val name: String,
    dirRelPath: String,
    private val typeId: String,
    initial: ModuleData?,
    private val codecs: FacetCodecRegistry,
) : ModifiableModule {

    val isNew: Boolean = initial == null
    var depsChanged = false; private set
    var sourceSetsChanged = false; private set
    var facetsChanged = false; private set
    var settingsChanged = false; private set

    private var dirRelPathField: String = dirRelPath
    private var languageLevelField: LanguageLevel = initial?.languageLevel ?: LanguageLevel.JAVA_17
    private var sdkField: SdkRef? = initial?.sdk?.let { SdkRef(it) }
    private var outputRelPathField: String = initial?.outputRelPath ?: "build/classes"
    private val deps = ArrayList<OrderEntry>(initial?.dependencies ?: emptyList())
    private val sourceSets = ArrayList<SourceSetData>(initial?.sourceSets ?: emptyList())
    private val facets = LinkedHashMap<String, FacetData>().apply { initial?.facets?.forEach { put(it.tomlTable, it) } }

    override var outputRelPath: String
        get() = outputRelPathField
        set(value) {
            if (value == outputRelPathField) return
            outputRelPathField = value
            settingsChanged = true
        }

    override var dirRelPath: String
        get() = dirRelPathField
        set(value) {
            if (value == dirRelPathField) return
            dirRelPathField = value
            settingsChanged = true
        }

    override var languageLevel: LanguageLevel
        get() = languageLevelField
        set(value) {
            languageLevelField = value
            settingsChanged = true
        }

    override var sdk: SdkRef?
        get() = sdkField
        set(value) {
            sdkField = value
            settingsChanged = true
        }

    override fun addDependency(entry: OrderEntry) {
        deps.add(entry)
        depsChanged = true
    }

    override fun removeDependency(entry: OrderEntry) {
        if (deps.remove(entry)) depsChanged = true
    }

    override fun addSourceSet(template: SourceSetTemplate) {
        val roots = template.roots.map { (dir, roles): Map.Entry<String, Set<ContentRole>> -> ContentRootData(dir, roles) }
        val idx = sourceSets.indexOfFirst { it.name == template.name }
        if (idx >= 0) {
            // Merge into the existing set rather than producing a duplicate SourceSetData.
            roots.forEach { mergeRoot(idx, it.dirRelPath, it.roles) }
        } else {
            sourceSets.add(SourceSetData(template.name, template.scope, roots))
        }
        sourceSetsChanged = true
    }

    override fun addContentRoot(sourceSetName: String, dirRelPath: String, roles: Set<ContentRole>) {
        val idx = sourceSets.indexOfFirst { it.name == sourceSetName }
        if (idx >= 0) {
            mergeRoot(idx, dirRelPath, roles)
        } else {
            sourceSets.add(SourceSetData(sourceSetName, DependencyScope.IMPLEMENTATION, listOf(ContentRootData(dirRelPath, roles))))
        }
        sourceSetsChanged = true
    }

    override fun removeContentRoot(sourceSetName: String, dirRelPath: String) {
        val idx = sourceSets.indexOfFirst { it.name == sourceSetName }
        if (idx < 0) return
        val ss = sourceSets[idx]
        val remaining = ss.contentRoots.filterNot { it.dirRelPath == dirRelPath }
        if (remaining.size != ss.contentRoots.size) {
            sourceSets[idx] = ss.copy(contentRoots = remaining)
            sourceSetsChanged = true
        }
    }

    /** Give the [sourceSetName] set [scope]. False when there is no such set or it already has that scope. */
    fun setSourceSetScope(sourceSetName: String, scope: DependencyScope): Boolean {
        val idx = sourceSets.indexOfFirst { it.name == sourceSetName }
        if (idx < 0 || sourceSets[idx].scope == scope) return false
        sourceSets[idx] = sourceSets[idx].copy(scope = scope)
        sourceSetsChanged = true
        return true
    }

    /** Append [dirRelPath] to the set at [idx], merging [roles] into a root with the same path if present. */
    private fun mergeRoot(idx: Int, dirRelPath: String, roles: Set<ContentRole>) {
        val ss = sourceSets[idx]
        val existing = ss.contentRoots.indexOfFirst { it.dirRelPath == dirRelPath }
        val roots = ss.contentRoots.toMutableList()
        if (existing >= 0) {
            roots[existing] = roots[existing].copy(roles = roots[existing].roles + roles)
        } else {
            roots.add(ContentRootData(dirRelPath, roles))
        }
        sourceSets[idx] = ss.copy(contentRoots = roots)
    }

    override fun <T : Facet> putFacet(facet: T) {
        val fd = codecs.encode(facet) ?: error("no FacetCodec registered for facet '${facet.key.id}'")
        putFacetData(fd)
    }

    override fun putFacetData(data: FacetData) {
        require(data.tomlTable !in RESERVED_FACET_TABLES) {
            "facet table '${data.tomlTable}' is one the model owns (${RESERVED_FACET_TABLES.joinToString()}); " +
                "writing it would overwrite the module's own configuration"
        }
        for ((key, value) in data.values) requireTomlValue(data.tomlTable, key, value)
        facets[data.tomlTable] = data
        facetsChanged = true
    }

    fun toData(): ModuleData = ModuleData(
        id = id,
        name = name,
        dirRelPath = dirRelPathField,
        typeId = typeId,
        languageLevel = languageLevelField,
        outputRelPath = outputRelPathField,
        sourceSets = sourceSets.toList(),
        dependencies = deps.toList(),
        facets = facets.values.toList(),
        sdk = sdkField?.name,
    )
}

/**
 * Reject a facet value `module.toml` cannot hold, at the point it is staged.
 *
 * The TOML writer fails on one too, but only at the next save, by which time the offending table is one of many
 * in a document being written for an unrelated reason and the caller that staged it is long gone. The set
 * accepted here is exactly what the writer formats.
 */
private fun requireTomlValue(table: String, key: String, value: Any?) {
    when (value) {
        null -> throw IllegalArgumentException(
            "facet table '$table', key '$key': null has no TOML representation, omit the key instead",
        )
        is String, is Boolean, is Int, is Long, is Double, is Float -> Unit
        is List<*> -> for (item in value) requireTomlValue(table, key, item)
        is Map<*, *> -> for ((k, v) in value) {
            require(k is String) {
                "facet table '$table', key '$key': inline-table keys must be String, was ${k?.let { it::class.simpleName }}"
            }
            requireTomlValue(table, key, v)
        }
        else -> throw IllegalArgumentException(
            "facet table '$table', key '$key': ${value::class.simpleName} has no TOML representation",
        )
    }
}

/**
 * Give an existing source set a new [scope], which decides what it builds into: a test-only scope
 * ([DependencyScope.TEST_IMPLEMENTATION]) makes it a test set, compiled apart from the main output against the
 * test classpath. A new set takes its scope from the [SourceSetTemplate] it is added with.
 *
 * Not a member of [ModifiableModule]: that interface is published plugin SPI, and changing it waits for an
 * SPI release. False when this module did not come from a transaction of this store, when it has no such set,
 * or when the set already has [scope].
 */
fun ModifiableModule.setSourceSetScope(sourceSetName: String, scope: DependencyScope): Boolean =
    (this as? ModuleBuilder)?.setSourceSetScope(sourceSetName, scope) ?: false
