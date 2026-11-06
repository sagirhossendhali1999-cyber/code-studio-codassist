package com.shd.codestudio.core.plugins

import com.shd.codestudio.agent.ui.AgentUiPlugin
import com.shd.codestudio.analysis.ACTION_PROVIDER_EP
import com.shd.codestudio.analysis.ANALYZER_EP
import com.shd.codestudio.analysis.MODULE_ANALYSIS
import com.shd.codestudio.analysis.QUICK_FIX_PROVIDER_EP
import com.shd.codestudio.android.support.ANDROID_RESOURCE_REPOSITORY
import com.shd.codestudio.android.support.AndroidAidlProvider
import com.shd.codestudio.android.support.AndroidBuildConfigProvider
import com.shd.codestudio.android.support.AndroidRClassProvider
import com.shd.codestudio.android.support.AndroidSupport
import com.shd.codestudio.android.support.AndroidViewBindingProvider
import com.shd.codestudio.android.support.ResourceRepositorySource
import com.shd.codestudio.android.support.index.AndroidResourceIndex
import com.shd.codestudio.android.support.index.AndroidResourceRefIndex
import com.shd.codestudio.android.support.metadata.AndroidSdkMetadata
import com.shd.codestudio.block.BLOCK_MAPPING_EP
import com.shd.codestudio.block.impl.JavaBlockMapping
import com.shd.codestudio.block.impl.KotlinBlockMapping
import com.shd.codestudio.build.BUILD_CONTROL
import com.shd.codestudio.build.KOTLIN_COMPILER_PLUGIN_EP
import com.shd.codestudio.build.BUILD_PLUGIN_EP
import com.shd.codestudio.build.SOURCE_GENERATOR_EP
import com.shd.codestudio.core.ACTION_MANAGER
import com.shd.codestudio.core.keymap.EditorKeymapPlugin
import com.shd.codestudio.core.EDITOR_DECORATIONS
import com.shd.codestudio.core.ANDROID_RESOURCE_SERVICE
import com.shd.codestudio.core.ActiveEngineXmlResourceHost
import com.shd.codestudio.core.ApplicationEnvironment
import com.shd.codestudio.core.BLOCK_SERVICE
import com.shd.codestudio.core.BUILD_SERVICE
import com.shd.codestudio.core.COMPOSE_PREVIEW_SERVICE
import com.shd.codestudio.core.DEPENDENCY_SERVICE
import com.shd.codestudio.core.ENGINE_CONTEXT
import com.shd.codestudio.core.ICON_MANAGER_SERVICE
import com.shd.codestudio.core.INTERPRETER_LOWERING
import com.shd.codestudio.core.KOTLIN_EDITOR_SERVICE
import com.shd.codestudio.core.KOTLIN_PLUGIN_LOADER
import com.shd.codestudio.core.LANGUAGE_FEATURE_SERVICE
import com.shd.codestudio.core.MODULE_ANALYZERS
import com.shd.codestudio.core.MODULE_SERVICE
import com.shd.codestudio.core.PROJECT_SYNC_SERVICE
import com.shd.codestudio.core.REFACTOR_SERVICE
import com.shd.codestudio.core.APP_RENAME_SERVICE
import com.shd.codestudio.core.SEARCH_SERVICE
import com.shd.codestudio.core.SIGNING_SERVICE
import com.shd.codestudio.core.actions.BuiltInActions
import com.shd.codestudio.core.actions.EditorTextActions
import com.shd.codestudio.core.actions.ExtractFileAction
import com.shd.codestudio.core.analysis.AidlAnalyzer
import com.shd.codestudio.core.analysis.ModuleAnalyzers
import com.shd.codestudio.core.analysis.PackageMismatchAnalyzer
import com.shd.codestudio.core.analysis.PluginManifestAnalyzer
import com.shd.codestudio.core.android.AndroidXmlActionProvider
import com.shd.codestudio.core.android.AndroidXmlChecker
import com.shd.codestudio.core.android.AndroidXmlTagChecker
import com.shd.codestudio.core.completion.BufferWordsContributor
import com.shd.codestudio.core.completion.CompletionStats
import com.shd.codestudio.core.completion.MemberUsageIndex
import com.shd.codestudio.core.completion.PluginManifestCompletion
import com.shd.codestudio.core.completion.PostfixContributor
import com.shd.codestudio.core.completion.UserLiveTemplateContributor
import com.shd.codestudio.core.gradle.GradleBuildFileWriter
import com.shd.codestudio.core.gradle.GradleModelImporter
import com.shd.codestudio.core.gradle.GradleProjectImporter
import com.shd.codestudio.core.services.AndroidResourceService
import com.shd.codestudio.core.services.BlockService
import com.shd.codestudio.core.services.BuildService
import com.shd.codestudio.core.services.ComposePreviewService
import com.shd.codestudio.core.services.DependencyService
import com.shd.codestudio.core.services.IconManagerService
import com.shd.codestudio.core.services.KotlinEditorService
import com.shd.codestudio.core.services.LanguageFeatureService
import com.shd.codestudio.core.services.ModuleService
import com.shd.codestudio.core.services.RefactorService
import com.shd.codestudio.core.services.AppRenameService
import com.shd.codestudio.core.services.SearchService
import com.shd.codestudio.core.services.SigningService
import com.shd.codestudio.core.sync.ProjectSyncService
import com.shd.codestudio.core.templates.CalculatorSampleTemplate
import com.shd.codestudio.core.templates.Code StudioPluginTemplate
import com.shd.codestudio.core.templates.JavaConsoleAppTemplate
import com.shd.codestudio.core.templates.JavaLibraryTemplate
import com.shd.codestudio.templates.JavaLibModuleType
import com.shd.codestudio.templates.BUILT_IN_KOTLIN_TEMPLATES
import com.shd.codestudio.core.templates.NotesSampleTemplate
import com.shd.codestudio.core.templates.SwingAppTemplate
import com.shd.codestudio.core.templates.KmpComposeAppTemplate
import com.shd.codestudio.core.templates.KmpLibraryTemplate
import com.shd.codestudio.core.templates.SwingCanvasTemplate
import com.shd.codestudio.core.templates.WeatherSampleTemplate
import com.shd.codestudio.index.INDEX_EP
import com.shd.codestudio.index.SYMBOL_SEARCH
import com.shd.codestudio.ksp.DefaultKspProcessorLoader
import com.shd.codestudio.ksp.KspProcessorCatalog
import com.shd.codestudio.ksp.KspProcessorLoader
import com.shd.codestudio.ksp.KspSourceGenerator
import com.shd.codestudio.lang.FILE_TYPE_EP
import com.shd.codestudio.lang.FileTypeMapping
import com.shd.codestudio.lang.LANGUAGE_BACKEND_EP
import com.shd.codestudio.lang.LanguageId
import com.shd.codestudio.lang.completion.COMPLETION_CONTRIBUTOR_EP
import com.shd.codestudio.lang.completion.COMPLETION_WEIGHER_EP
import com.shd.codestudio.lang.completion.CompletionContribution
import com.shd.codestudio.lang.completion.ProjectUsageWeigher
import com.shd.codestudio.lang.completion.StatsWeigher
import com.shd.codestudio.lang.java.JavaLanguageBackend
import com.shd.codestudio.lang.java.index.JavaClassLocatorIndex
import com.shd.codestudio.lang.java.index.JavaClassNamesIndex
import com.shd.codestudio.lang.java.index.JavaMainIndex
import com.shd.codestudio.lang.java.index.JavaMembersByOwnerIndex
import com.shd.codestudio.lang.java.index.JavaMembersIndex
import com.shd.codestudio.lang.java.index.JavaPackageTypesIndex
import com.shd.codestudio.lang.java.index.JavaPackagesIndex
import com.shd.codestudio.lang.java.index.JavaSourceAnnotationIndex
import com.shd.codestudio.lang.java.index.JavaSourceDocIndex
import com.shd.codestudio.lang.java.index.JavaSourceSubtypeIndex
import com.shd.codestudio.lang.java.index.JavaSourceSymbolsIndex
import com.shd.codestudio.lang.jdt.analysis.JdtAnalysisSupport
import com.shd.codestudio.lang.kotlin.KotlinLanguageBackend
import com.shd.codestudio.lang.kotlin.analysis.KotlinAnalysisSupport
import com.shd.codestudio.build.jvm.compose.ComposeResourcesFacetCodec
import com.shd.codestudio.build.jvm.compose.ComposeResourcesPlugin
import com.shd.codestudio.lang.kotlin.build.KotlinFacetCodec
import com.shd.codestudio.lang.kotlin.build.KotlinFileIconProvider
import com.shd.codestudio.lang.kotlin.compile.ComposeCompilerPlugin
import com.shd.codestudio.lang.kotlin.compile.ParcelizeCompilerPlugin
import com.shd.codestudio.lang.kotlin.compile.SerializationCompilerPlugin
import com.shd.codestudio.lang.kotlin.completion.KotlinPostfixTemplates
import com.shd.codestudio.lang.kotlin.index.BinaryAnnotationIndex
import com.shd.codestudio.lang.kotlin.index.BinarySubtypeIndex
import com.shd.codestudio.lang.kotlin.index.KotlinBuiltinCallableIndex
import com.shd.codestudio.lang.kotlin.index.KotlinBuiltinsIndex
import com.shd.codestudio.lang.kotlin.index.KotlinCallableIndex
import com.shd.codestudio.lang.kotlin.index.KotlinClassNamesIndex
import com.shd.codestudio.lang.kotlin.index.KotlinMainIndex
import com.shd.codestudio.lang.kotlin.index.KotlinMembersIndex
import com.shd.codestudio.lang.kotlin.index.KotlinPackageDeclIndex
import com.shd.codestudio.lang.kotlin.index.KotlinPackageTypesIndex
import com.shd.codestudio.lang.kotlin.index.KotlinPackagesIndex
import com.shd.codestudio.lang.kotlin.index.KotlinSourceAnnotationIndex
import com.shd.codestudio.lang.kotlin.index.KotlinSourceCallableIndex
import com.shd.codestudio.lang.kotlin.index.KotlinSourceDocIndex
import com.shd.codestudio.lang.kotlin.index.KotlinSourceSubtypeIndex
import com.shd.codestudio.lang.kotlin.index.KotlinSourceSymbolsIndex
import com.shd.codestudio.lang.kotlin.index.KotlinTypeShapeIndex
import com.shd.codestudio.lang.kotlin.synthetic.KotlinSyntheticClassProvider
import com.shd.codestudio.lang.postfix.POSTFIX_TEMPLATE_EP
import com.shd.codestudio.lang.synthetic.SYNTHETIC_CLASS_EP
import com.shd.codestudio.lang.xml.XmlLanguageBackend
import com.shd.codestudio.lang.xml.lint.XmlAnalysisSupport
import com.shd.codestudio.model.FACET_CODEC_EP
import com.shd.codestudio.model.FacetCodecRegistry
import com.shd.codestudio.model.FileIconRegistry
import com.shd.codestudio.model.MODULE_RESOURCES
import com.shd.codestudio.model.MODULE_SOURCES
import com.shd.codestudio.model.ModuleTypeRegistry
import com.shd.codestudio.model.ProjectTemplateRegistry
import com.shd.codestudio.model.impl.DefaultFileIconProvider
import com.shd.codestudio.model.module
import com.shd.codestudio.model.sync.BUILD_FILE_WRITER_EP
import com.shd.codestudio.model.sync.PROJECT_IMPORTER_EP
import com.shd.codestudio.platform.ServiceScopeLevel
import com.shd.codestudio.core.services.KotlinProgramLowering
import com.shd.codestudio.interp.api.CODE_INTERPRETER
import com.shd.codestudio.interp.api.LowerResult
import com.shd.codestudio.interp.impl.CodeInterpreterImpl
import com.shd.codestudio.interp.impl.RESTRICT_ALL
import com.shd.codestudio.plugin.Plugin
import com.shd.codestudio.plugin.PluginManifest
import com.shd.codestudio.plugin.PluginRegistration
import com.shd.codestudio.plugin.impl.ActionManager
import com.shd.codestudio.plugin.impl.EditorDecorationCollector
import com.shd.codestudio.ui.ext.UiPlugin
import com.shd.codestudio.vcs.ui.VcsUiPlugin
import java.nio.file.Files
import java.nio.file.Paths

/**
 * The IDE's own built-in plugins and the ordered set the [ApplicationEnvironment] loads.
 *
 * This is the whole host-wiring, expressed AS plugins: the imperative `registerStaticPlugins`/
 * `registerEngineServices`/`registerActiveEnginePlugins` block that used to live in the [IdeServices]
 * companion is now a set of built-in [Plugin]s driven by the [com.shd.codestudio.plugin.impl.PluginManager]. Each maps
 * 1:1 to the [com.shd.codestudio.platform.PluginId] it contributed under before, so the resolved registry is identical.
 *
 * What used to be implicit registration *sequencing* is now declared load-order:
 *  - `jdt-language` (the `.java` file type + ecj compiler) has no dependency and loads first; the language
 *    backends `dependsOn` it. `java-psi-language` loads next, so `JavaLanguageBackend` is index 0 on
 *    [LANGUAGE_BACKEND_EP] — the resolution fallback `backendFor` relies on. Both are essential.
 *
 * Contributions that must reach the currently-open project (the acceptance-stats weigher, the XML resource
 * host, the app-compat action, the command actions) take [ApplicationEnvironment] and read `env.activeEngine`
 * lazily at callback time, never during `register`. Prefer a scoped service where the contribution is handed
 * a [com.shd.codestudio.model.Workspace] or [com.shd.codestudio.model.Module] to resolve through, as the synthetic `R` does: that
 * reads the model actually being asked about instead of whichever project is open.
 */
/**
 * One built-in feature's UNIFIED registration: its engine [Plugin] (the identity — manifest/id/enabled state
 * live here) paired with an OPTIONAL Compose [UiPlugin] facet. The two facets can't be one object (platform-core
 * vs Compose worlds; a `@Composable` body can't live in the engine module), so a feature co-declares them here
 * in one entry. [ApplicationEnvironment] loads the engine facet into the plugin manager and exposes the UI facet
 * of ENABLED plugins to the shell — so disabling the plugin drops BOTH halves through the one decision.
 */
class BuiltInPlugin(val engine: Plugin, val ui: UiPlugin? = null)

object BuiltInPlugins {
    fun assemble(env: ApplicationEnvironment, codecs: FacetCodecRegistry): List<BuiltInPlugin> = listOf(
        BuiltInPlugin(PlatformPlugin()),
        // The keymap installs the resolver the editor's key handler asks, so it loads early: the editor's
        // built-in defaults answer until it does, but a user's rebinding does not.
        BuiltInPlugin(EditorKeymapPlugin(preference = env.preferences)),
        BuiltInPlugin(JdtLanguagePlugin()),
        BuiltInPlugin(JavaPsiLanguagePlugin()),
        BuiltInPlugin(XmlLanguagePlugin()),
        BuiltInPlugin(KotlinLanguagePlugin()),
        BuiltInPlugin(JavaSupportPlugin()),
        BuiltInPlugin(KotlinSupportPlugin()),
        BuiltInPlugin(KspSupportPlugin(env)),
        BuiltInPlugin(GradleSupportPlugin()),
        BuiltInPlugin(BlocksPlugin()),
        BuiltInPlugin(AndroidSupportPlugin(codecs)),
        BuiltInPlugin(SamplesPlugin()),
        BuiltInPlugin(PluginDevPlugin(env)),
        BuiltInPlugin(CompletionBuiltinsPlugin(env)),
        BuiltInPlugin(IndexingPlugin()),
        BuiltInPlugin(JdtAnalysisPlugin()),
        BuiltInPlugin(JavaPsiAnalysisPlugin()),
        BuiltInPlugin(KotlinAnalysisPlugin()),
        BuiltInPlugin(PackageMismatchPlugin()),
        BuiltInPlugin(ManifestPermissionPlugin(env)),
        BuiltInPlugin(MissingLibraryPlugin(env)),
        BuiltInPlugin(XmlAnalysisPlugin(env)),
        BuiltInPlugin(AndroidXmlPlugin(env)),
        BuiltInPlugin(IdeCoreServicesPlugin()),
        BuiltInPlugin(InterpreterPlugin(env)),
        BuiltInPlugin(IdeCoreActionsPlugin(env)),
        BuiltInPlugin(EditorTextActionsPlugin()),
        // The AI agent: engine facet (settings page + AgentBackend wiring) + its Compose chat UI, one entry.
        BuiltInPlugin(AgentPlugin(), ui = AgentUiPlugin),
        // Version control: engine facet (settings page + VcsBackend wiring) + its Compose Git UI.
        BuiltInPlugin(VcsPlugin(), ui = VcsUiPlugin),
    )
}

/** The platform baseline: the default file-icon classifier. */
private class PlatformPlugin : Plugin {
    override val manifest = PluginManifest(
        id = "platform", name = "Platform", essential = true,
        description = "Core file-icon classifier and base file-type mappings.",
    )

    override fun register(reg: PluginRegistration) {
        reg.contributeVia { ext, pid ->
            FileIconRegistry(ext).register(
                DefaultFileIconProvider,
                pid
            )
        }
        // Markdown has no language backend; the mapping keeps a .md file from being analysed as Java.
        reg.register(
            FILE_TYPE_EP,
            FileTypeMapping(listOf(".md", ".markdown"), LanguageId("markdown"))
        )
    }
}

/**
 * The `.java` file-type owner. JDT is no longer the `.java` EDITOR backend (that is [JavaPsiLanguagePlugin]);
 * ecj lives on as the build compiler ([com.shd.codestudio.lang.jdt.build] / `:jvm-build`) and as the `.java` compile-level
 * [com.shd.codestudio.analysis.DiagnosticProvider] + quick-fixes ([JdtAnalysisPlugin]). This plugin just registers the
 * `.java → java` file-type association (kept here as the historical/essential owner). It stays first so it
 * loads before the language backends that `dependsOn` it.
 */
private class JdtLanguagePlugin : Plugin {
    override val manifest = PluginManifest(
        id = "jdt-language", name = "Java (file type + compiler)", essential = true,
        description = "Owns the .java file type; ecj remains the Java build compiler and compile-level diagnostic provider.",
    )

    override fun register(reg: PluginRegistration) {
        reg.register(FILE_TYPE_EP, FileTypeMapping(listOf(".java"), LanguageId("java")))
    }
}

/**
 * The IntelliJ-PSI Java backend (`:lang-java`) — THE `.java` editor backend: IntelliJ's Java parser + native
 * resolution/inference for completion, navigation, folding/highlight/signature/inlay, rename, and
 * unresolved-symbol diagnostics. It is the only backend claiming `LanguageId("java")`, so `backendFor("java")`
 * resolves to it and the module's `ANALYZER_JAVA` builds a `JavaSourceAnalyzer`. `dependsOn` jdt-language so
 * the `.java` file type + the ecj diagnostic/quick-fix providers are registered first. `essential` because it
 * is the sole Java editor backend and the first registrant on [LANGUAGE_BACKEND_EP] — the resolution fallback
 * `backendFor` relies on — so it must not be disablable.
 *
 * Formatting is covered at the host level (`LanguageFeatureService` reuses JDT's `CodeFormatter` for `.java`).
 * Not-yet-covered vs the old JDT editor (see docs): compile-level diagnostics beyond unresolved references
 * (type mismatch, missing return, …) — those still come from the ecj `CompilerDiagnosticProvider` only when it
 * can reconcile via a JDT analyzer, so they are currently absent under this backend.
 */
private class JavaPsiLanguagePlugin : Plugin {
    override val manifest = PluginManifest(
        id = "java-psi-language",
        name = "Java Language (IntelliJ PSI)",
        essential = true,
        description = "Java editing via IntelliJ's Java PSI parser and native resolution/inference (the .java editor backend).",
        dependsOn = listOf("jdt-language"),
    )

    override fun register(reg: PluginRegistration) {
        reg.register(LANGUAGE_BACKEND_EP, JavaLanguageBackend())
    }
}

/** XML language backend (Android layouts/values/manifest). */
private class XmlLanguagePlugin : Plugin {
    override val manifest = PluginManifest(
        id = "xml-language",
        name = "XML Language",
        description = "XML editing for Android layouts, values, manifest, and drawables (tolerant parser + completion).",
        dependsOn = listOf("jdt-language"),
    )

    override fun register(reg: PluginRegistration) {
        reg.register(LANGUAGE_BACKEND_EP, XmlLanguageBackend())
        reg.register(FILE_TYPE_EP, FileTypeMapping(listOf(".xml"), XmlLanguageBackend.LANGUAGE_ID))
    }
}

/** Kotlin language backend (editor-only). */
private class KotlinLanguagePlugin : Plugin {
    override val manifest = PluginManifest(
        id = "kotlin-language",
        name = "Kotlin Language",
        description = "Kotlin editing: parsing, code completion, and navigation (editor-only).",
        dependsOn = listOf("jdt-language"),
    )

    override fun register(reg: PluginRegistration) {
        reg.register(LANGUAGE_BACKEND_EP, KotlinLanguageBackend())
        reg.register(
            FILE_TYPE_EP,
            FileTypeMapping(listOf(".kt", ".kts"), KotlinLanguageBackend.LANGUAGE_ID)
        )
    }
}

/** Java support: the java-library module type and the Java Create-Project templates. */
private class JavaSupportPlugin : Plugin {
    override val manifest = PluginManifest(
        id = "java-support", name = "Java Support",
        description = "Java-library module type and Java Create-Project templates.",
    )
    override fun register(reg: PluginRegistration) {
        reg.contributeVia { ext, pid ->
            ModuleTypeRegistry(ext).register(JavaLibModuleType, pid)
            val templates = ProjectTemplateRegistry(ext)
            templates.register(JavaConsoleAppTemplate, pid)
            templates.register(JavaLibraryTemplate, pid)
            templates.register(SwingAppTemplate, pid)
            templates.register(SwingCanvasTemplate, pid)
        }
    }
}

/**
 * Gradle compatibility: the [GradleProjectImporter] that reads a Gradle project's scripts into the project
 * model ([PROJECT_IMPORTER_EP]) and the [GradleBuildFileWriter] that writes dependency declarations back into
 * `build.gradle(.kts)` ([BUILD_FILE_WRITER_EP]), plus the Kotlin Multiplatform Create-Project templates, which
 * write Gradle projects for that importer. Disabling it leaves Gradle folders unrecognized: they open
 * as empty native workspaces instead of imported projects.
 */
private class GradleSupportPlugin : Plugin {
    override val manifest = PluginManifest(
        id = "gradle-support", name = "Gradle Support",
        description = "Opens Gradle projects by reading their build scripts statically (and JetBrains Amper projects from their YAML), writes dependency declarations back to Gradle scripts, and adds the Kotlin Multiplatform project templates.",
    )

    override fun register(reg: PluginRegistration) {
        reg.register(PROJECT_IMPORTER_EP, GradleProjectImporter())
        // JetBrains Amper (`project.yaml` + `module.yaml`), read the same way: the files stay the source of truth.
        reg.register(PROJECT_IMPORTER_EP, com.shd.codestudio.core.amper.AmperProjectImporter())
        // A build that exported its own configured model (`.platform/gradle-model.json`) is imported from
        // that instead of from its script text, which is the only way a build whose structure is computed
        // rather than declared can be read at all. It detects with a higher confidence, so it wins wherever
        // both apply.
        reg.register(PROJECT_IMPORTER_EP, GradleModelImporter())
        reg.register(BUILD_FILE_WRITER_EP, GradleBuildFileWriter())
        // The Kotlin Multiplatform templates write a Gradle project for the importer above to read in (a
        // multi-target build has no other shape), so they come and go with it.
        reg.contributeVia { ext, pid ->
            val templates = ProjectTemplateRegistry(ext)
            templates.register(KmpComposeAppTemplate, pid)
            templates.register(KmpLibraryTemplate, pid)
        }
    }
}

/**
 * The projectional (block) editor: contributes the Java and Kotlin block decompositions ([JavaBlockMapping],
 * [KotlinBlockMapping]) onto [BLOCK_MAPPING_EP], the one thing that makes the Code/Blocks toggle do anything.
 * Non-essential: disabling it drops every block mapping, so the engine's [com.shd.codestudio.core.services.BlockService] reports no mappings and
 * the UI hides the Blocks view-mode segment (the whole feature turns off through this one decision). The
 * generic projection plumbing (the WORKSPACE-scoped `BlockService`) stays in [IdeCoreServicesPlugin]; with no
 * mapping registered it is simply inert.
 */
private class BlocksPlugin : Plugin {
    override val manifest = PluginManifest(
        id = "blocks", name = "Block Editor",
        description = "The projectional block editor — a Scratch-style visual view of the same code, toggled per file.",
    )
    override fun register(reg: PluginRegistration) {
        reg.register(BLOCK_MAPPING_EP, JavaBlockMapping)
        reg.register(BLOCK_MAPPING_EP, KotlinBlockMapping)
    }
}

/** Kotlin support: the Kotlin-interop synthetic classes, Kotlin Create-Project templates, and the built-in
 *  Compose Kotlin-compiler plugin (the build's compileKotlin tasks read it off the EP). */
private class KotlinSupportPlugin : Plugin {
    override val manifest = PluginManifest(
        id = "kotlin-support",
        name = "Kotlin Support",
        description = "Kotlin-interop synthetic classes (for java), Kotlin Create-Project templates, and the built-in Compose Kotlin-compiler plugin."
    )
    override fun register(reg: PluginRegistration) {
        reg.register(SYNTHETIC_CLASS_EP, KotlinSyntheticClassProvider())
        // `[kotlin]` in module.toml: today just `multiplatform`, which decides whether a module's
        // `expect`/`actual` declarations compile at all (see KotlinFacet).
        reg.register(FACET_CODEC_EP, KotlinFacetCodec)
        // The multiplatform module mark in the project tree.
        reg.contributeVia { ext, pid -> FileIconRegistry(ext).register(KotlinFileIconProvider, pid) }
        // Compose Multiplatform resources: `[composeResources]` plus the build plugin that generates the
        // `Res` class and stages the resource tree, which the Compose Gradle plugin would otherwise be the
        // only producer of.
        reg.register(FACET_CODEC_EP, ComposeResourcesFacetCodec)
        reg.register(BUILD_PLUGIN_EP, ComposeResourcesPlugin())
        // gmazzo's `buildConfig { }`: the Kotlin BuildConfig object a KMP/JVM module's code reads.
        reg.register(FACET_CODEC_EP, com.shd.codestudio.build.jvm.buildconfig.KotlinBuildConfigFacetCodec)
        reg.register(BUILD_PLUGIN_EP, com.shd.codestudio.build.jvm.buildconfig.KotlinBuildConfigPlugin())
        reg.register(KOTLIN_COMPILER_PLUGIN_EP, ComposeCompilerPlugin)
        reg.register(KOTLIN_COMPILER_PLUGIN_EP, SerializationCompilerPlugin)
        reg.register(KOTLIN_COMPILER_PLUGIN_EP, ParcelizeCompilerPlugin)
        // Editor support for a compiler plugin's generated members (kotlinx.serialization's `Foo.serializer()`,
        // Parcelize's `Foo.CREATOR`): the parse-only symbol model can't see them, so a provider contributes them
        // to completion/resolution.
        reg.register(
            com.shd.codestudio.lang.kotlin.symbols.KOTLIN_SYNTHETIC_MEMBER_EP,
            com.shd.codestudio.lang.kotlin.symbols.SerializationSyntheticMembers,
        )
        reg.register(
            com.shd.codestudio.lang.kotlin.symbols.KOTLIN_SYNTHETIC_MEMBER_EP,
            com.shd.codestudio.lang.kotlin.symbols.ParcelizeSyntheticMembers,
        )
        reg.contributeVia { ext, pid ->
            val templates = ProjectTemplateRegistry(ext)
            BUILT_IN_KOTLIN_TEMPLATES.forEach { templates.register(it, pid) }
        }
    }
}

/**
 * KSP2 source generation: contributes [KspSourceGenerator] on [SOURCE_GENERATOR_EP], so the build's
 * `generateSources` tasks run the IDE's **bundled** KSP2 processors (Room, …) on the IDE's OWN compiler/AA
 * (the ~776 KB thin runner + the bundled processor jars — nothing 78 MB or downloaded, so it stays within
 * Play's dynamic-code-loading policy). Activation is marker + declared-dependency: a processor runs when its
 * runtime is a **directly-declared** dependency of the module (added via the Dependencies screen or the KSP
 * toggle) and its marker is on the compile classpath. Matching AGP's explicit `ksp(...)` opt-in, a runtime
 * that only arrives transitively (e.g. a Compose app that pulls `room-runtime` through another library but
 * never declares Room) does NOT activate the processor. The generated `.kt`/`.java` land in the module's
 * `ContentRole.GENERATED` root and compile + index like hand-written code.
 *
 * The processor classloader is the injected [KOTLIN_PLUGIN_LOADER] (a plain `URLClassLoader` on desktop, a
 * `DexClassLoader` over bundled dex on ART) — its parent is the app classloader, which carries our compiler/AA
 * + `symbol-processing-api`, so the thin runner + processors resolve those parent-first. `jdkHome` is a real
 * JDK on desktop and null on ART (where android.jar on the module's compile classpath supplies `java.*`).
 */
private class KspSupportPlugin(private val env: ApplicationEnvironment) : Plugin {
    override val manifest = PluginManifest(
        id = "ksp-support", name = "KSP Source Generation",
        description = "Runs bundled KSP2 processors (Room, …) at build time on the IDE's own compiler; generated sources compile + index like hand-written code.",
    )

    override fun register(reg: PluginRegistration) {
        val catalog = KspProcessorCatalog.bundled()
        // Reuse the injected Kotlin-plugin loader; read lazily (it may be registered after assemble()), falling
        // back to the desktop URLClassLoader when no host loader is wired.
        val loader = KspProcessorLoader { cp ->
            env.container.getServiceOrNull(KOTLIN_PLUGIN_LOADER)?.load(cp) ?: DefaultKspProcessorLoader.load(cp)
        }
        // A real modular JDK (desktop) exposes lib/jrt-fs.jar; ART's java.home does not — there KSP resolves
        // java.* from android.jar on the module's compile classpath instead, so jdkHome stays null.
        val jdkHome = System.getProperty("java.home")?.let { Paths.get(it) }
            ?.takeIf { Files.exists(it.resolve("lib/jrt-fs.jar")) }
        reg.register(
            SOURCE_GENERATOR_EP,
            KspSourceGenerator(
                processors = { req -> catalog.classpathFor(req.classpath, req.declaredDependencies) },
                // The IDE runs the processor version it BUNDLES, so a project pinning an older runtime gets
                // generated sources its own runtime can't compile. Report that up front instead of letting the
                // module fail on the symbols it produces. A mismatch the user accepted (the editor banner's
                // "build anyway") arrives on the request and becomes a per-build warning instead.
                preflight = { req ->
                    catalog.preflight(req.classpath, req.declaredDependencies, req.acceptedWarnings)
                },
                // The processor options a library's own Gradle plugin would contribute (Hilt's
                // `disableAndroidSuperclassValidation`), which no project build file spells out.
                processorOptions = { req -> catalog.optionsFor(req.classpath, req.declaredDependencies) },
                loader = loader,
                jdkHome = jdkHome,
            ),
        )
    }
}

/**
 * The android-support plugin: module types + the AndroidFacet codec + tree icons + templates + Compose sample
 * games (via the [AndroidSupport] facades, which attribute to `PluginId("android-support")`), the static
 * synthetic classes (BuildConfig, ViewBinding), and the light synthetic `R`, which reads the engine's shared
 * resource repositories through the workspace-scoped [ANDROID_RESOURCE_REPOSITORY] this plugin publishes.
 */
private class AndroidSupportPlugin(
    private val codecs: FacetCodecRegistry,
) : Plugin {
    override val manifest = PluginManifest(
        id = "android-support", name = "Android Support",
        description = "Android module types, the AndroidFacet + its module.toml codec, variants, resource icons, templates, and synthetic R / BuildConfig / ViewBinding / AIDL classes.",
    )
    override fun register(reg: PluginRegistration) {
        reg.contributeVia { ext, _ ->
            AndroidSupport.register(ModuleTypeRegistry(ext), codecs)
            AndroidSupport.registerIcons(FileIconRegistry(ext))
            AndroidSupport.registerTemplates(ProjectTemplateRegistry(ext))
            AndroidSupport.registerComposeSamples(ProjectTemplateRegistry(ext))
        }

        reg.register(SYNTHETIC_CLASS_EP, AndroidBuildConfigProvider())
        reg.register(SYNTHETIC_CLASS_EP, AndroidViewBindingProvider())
        // The engine's cached, buffer-aware resource repositories, published on the workspace being asked
        // about. The synthetic `R` resolves it through the [Workspace] it is handed, so it always reads the
        // cache of the project it is generating for rather than whichever project happens to be open.
        reg.service(ANDROID_RESOURCE_REPOSITORY, ServiceScopeLevel.WORKSPACE) {
            val ctx = getService(ENGINE_CONTEXT)
            ResourceRepositorySource { module, _ -> ctx.resourceRepo(module) }
        }
        reg.register(SYNTHETIC_CLASS_EP, AndroidRClassProvider())
        reg.register(SYNTHETIC_CLASS_EP, AndroidAidlProvider())
        // Editor diagnostics for `.aidl`, through the same parser and generator the build runs, so an
        // invalid interface is flagged as it is typed rather than at the next build.
        reg.contributeVia { ext, pid -> ext.register(ANALYZER_EP, AidlAnalyzer(), pid) }
        // ProGuard/R8 keep-rule files: routed off Java so JDT never flags them as broken Java.
        reg.register(FILE_TYPE_EP, FileTypeMapping(listOf(".pro"), LanguageId("proguard")))
        // AIDL: its own language id (no backend yet, so it edits as plain text with AIDL syntax colors),
        // which keeps JDT from analysing an interface definition as broken Java.
        reg.register(FILE_TYPE_EP, FileTypeMapping(listOf(".aidl"), LanguageId("aidl")))
    }
}

/** The bundled sample projects (Calculator, Notes, Weather) in the Create-Project gallery. */
private class SamplesPlugin : Plugin {
    override val manifest = PluginManifest(
        id = "samples", name = "Sample Projects",
        description = "Bundled sample projects (Calculator, Notes, Weather) in the Create-Project gallery.",
    )
    override fun register(reg: PluginRegistration) {
        reg.contributeVia { ext, pid ->
            val templates = ProjectTemplateRegistry(ext)
            templates.register(CalculatorSampleTemplate, pid)
            templates.register(NotesSampleTemplate, pid)
            templates.register(WeatherSampleTemplate, pid)
        }
    }
}

/**
 * Authoring plugins for this IDE: the Create-Project template that scaffolds one as its own app.
 *
 * Depends on `android-support` because a plugin project is an Android app module, so the `android-app`
 * module type has to be registered before the template can resolve it.
 */
private class PluginDevPlugin(private val env: ApplicationEnvironment) : Plugin {
    override val manifest = PluginManifest(
        id = "plugin-development", name = "Plugin Development",
        description = "Author Code Studio plugins: the Create-Project template for a plugin packaged as its " +
            "own app, and editor checks for its packaged manifest.",
        dependsOn = listOf("android-support"),
    )
    override fun register(reg: PluginRegistration) {
        reg.contributeVia { ext, pid ->
            ProjectTemplateRegistry(ext).register(Code StudioPluginTemplate, pid)
            // Runs the checks the loader makes, while the manifest is being edited rather than on whoever
            // installs the built plugin.
            ext.register(ANALYZER_EP, PluginManifestAnalyzer { env.hostVersion }, pid)
            // Completion in the same file. The project is resolved lazily through the active engine: this
            // registration is app-global, while the index it reads belongs to whichever project is open.
            ext.register(
                COMPLETION_CONTRIBUTOR_EP,
                CompletionContribution(
                    PluginManifestCompletion(
                        knownPluginIds = { env.pluginCatalog.all.map { m -> m.id }.sorted() },
                        index = { env.activeEngine?.indexService },
                    ),
                    languages = setOf(LanguageId("text")),
                ),
                pid,
            )
        }
    }
}

/** Cross-cutting completion: buffer-words + postfix contributors, Kotlin postfix templates, and the
 *  acceptance-frequency stats weigher (which counts through the active engine's per-project stats). */
private class CompletionBuiltinsPlugin(private val env: ApplicationEnvironment) : Plugin {
    override val manifest = PluginManifest(
        id = "completion-builtins", name = "Completion Built-ins",
        description = "Cross-language completion: buffer words, postfix templates, and acceptance-frequency ranking.",
    )

    override fun register(reg: PluginRegistration) {
        reg.register(
            COMPLETION_CONTRIBUTOR_EP,
            CompletionContribution(BufferWordsContributor, order = BufferWordsContributor.ORDER),
        )
        reg.contributeVia { ext, pid ->
            ext.register(
                COMPLETION_CONTRIBUTOR_EP,
                CompletionContribution(PostfixContributor(ext), order = PostfixContributor.ORDER),
                pid,
            )
            // User-defined live-template macros → completion, resolved through the open project's engine (its
            // effective set = built-ins ◂ global ◂ project). Language-agnostic registration; each macro's own
            // language scope is enforced inside the contributor.
            ext.register(
                COMPLETION_CONTRIBUTOR_EP,
                CompletionContribution(
                    UserLiveTemplateContributor { lang -> env.activeEngine?.userMacros(lang) ?: emptyList() },
                    order = UserLiveTemplateContributor.ORDER,
                ),
                pid,
            )
            KotlinPostfixTemplates.all().forEach { ext.register(POSTFIX_TEMPLATE_EP, it, pid) }
        }
        reg.register(
            COMPLETION_WEIGHER_EP,
            StatsWeigher { item ->
                env.activeEngine?.completionStats?.countFor(CompletionStats.keyOf(item.label)) ?: 0
            },
        )
        reg.register(
            COMPLETION_WEIGHER_EP,
            ProjectUsageWeigher { item -> env.activeEngine?.memberUsageCount(item.label) ?: 0 },
        )
    }
}

/**
 * All built-in symbol/member/resource index extensions, in registration order. Also what the prebuilt index
 * segments shipped in the APK are generated with ([com.shd.codestudio.core.PrebuiltIndexSegments]), so a segment built at
 * build time and one built on device come from the same extensions.
 */
internal val BUILT_IN_INDEX_EXTENSIONS: List<com.shd.codestudio.index.IndexExtension<*, *>> = listOf(
    JavaClassNamesIndex,
    JavaPackagesIndex,
    JavaPackageTypesIndex,
    JavaClassLocatorIndex,
    JavaSourceSymbolsIndex,
    JavaMembersIndex,
    JavaMembersByOwnerIndex,
    KotlinClassNamesIndex,
    KotlinPackagesIndex,
    KotlinPackageTypesIndex,
    KotlinSourceSymbolsIndex,
    KotlinMembersIndex,
    KotlinTypeShapeIndex,
    KotlinBuiltinsIndex,
    KotlinCallableIndex,
    KotlinBuiltinCallableIndex,
    KotlinSourceCallableIndex,
    KotlinPackageDeclIndex,
    JavaSourceDocIndex,
    KotlinSourceDocIndex,
    JavaMainIndex,
    KotlinMainIndex,
    AndroidResourceIndex,
    AndroidResourceRefIndex,
    BinarySubtypeIndex,
    BinaryAnnotationIndex,
    KotlinSourceSubtypeIndex,
    KotlinSourceAnnotationIndex,
    JavaSourceSubtypeIndex,
    JavaSourceAnnotationIndex,
    MemberUsageIndex,
)

/** Registers [BUILT_IN_INDEX_EXTENSIONS]. */
private class IndexingPlugin : Plugin {
    override val manifest = PluginManifest(
        id = "indexing", name = "Indexing",
        description = "Symbol, member, and Android-resource indexes powering completion, go-to, and search.",
    )
    override fun register(reg: PluginRegistration) {
        BUILT_IN_INDEX_EXTENSIONS.forEach { reg.register(INDEX_EP, it) }
    }
}

/** The Java (JDT) editor analysis surface (analyzers, compiler diagnostics, quick-fixes, intentions). */
private class JdtAnalysisPlugin : Plugin {
    override val manifest = PluginManifest(
        id = "jdt-analysis",
        name = "Java Analysis",
        description = "Java diagnostics, compiler errors, quick-fixes, and editor intentions (Eclipse JDT).",
        dependsOn = listOf("jdt-language"),
    )

    override fun register(reg: PluginRegistration) {
        reg.contributeVia { ext, pid -> JdtAnalysisSupport.register(ext, pid) }
    }
}

/**
 * The IntelliJ-PSI Java backend's native quick-fixes, keyed on its own diagnostic codes (type mismatch →
 * change variable type; unhandled exception → add to `throws` / surround with try-catch). `dependsOn`
 * java-psi-language (the backend that emits those codes). The JDT "Add import" fix (keyed on the neutral
 * `UNRESOLVED_REFERENCE`) still fires for this backend via jdt-analysis; this adds the ecj-free rest.
 */
private class JavaPsiAnalysisPlugin : Plugin {
    override val manifest = PluginManifest(
        id = "java-psi-analysis",
        name = "Java Analysis (IntelliJ PSI)",
        description = "Native Java quick-fixes for the IntelliJ-PSI backend (type mismatch, unhandled exception).",
        dependsOn = listOf("java-psi-language"),
    )

    override fun register(reg: PluginRegistration) {
        reg.contributeVia { ext, pid -> com.shd.codestudio.lang.java.analysis.JavaPsiAnalysisSupport.register(ext, pid) }
    }
}

/** The Kotlin editor analysis surface (diagnostics + import/implement-members code actions). */
private class KotlinAnalysisPlugin : Plugin {
    override val manifest =
        PluginManifest(
            id = "kotlin-analysis",
            name = "Kotlin Analysis",
            description = "Kotlin diagnostics plus import and implement-members code actions.",
            dependsOn = listOf("kotlin-language"),
        )

    override fun register(reg: PluginRegistration) {
        reg.contributeVia { ext, pid -> KotlinAnalysisSupport.register(ext, pid) }
    }
}

/**
 * The cross-language "package does not match file location" inspection (Java + Kotlin). Host-level because it
 * needs the file's module source roots (not just the language tree) to derive the expected package, and its
 * fix reuses both languages' package-text rewriters. `dependsOn` both language backends so the file types are
 * registered before it runs.
 */
private class PackageMismatchPlugin : Plugin {
    override val manifest = PluginManifest(
        id = "package-mismatch",
        name = "Package Mismatch Inspection",
        description = "Flags a Java/Kotlin file whose package does not match its directory, with a fix to correct it.",
        dependsOn = listOf("jdt-language", "kotlin-language"),
    )

    override fun register(reg: PluginRegistration) {
        reg.contributeVia { ext, pid -> ext.register(ANALYZER_EP, PackageMismatchAnalyzer(), pid) }
    }
}

/**
 * The missing-permission check's manifest fix (Java + Kotlin): add the `<uses-permission>` a call needs to the
 * module's `AndroidManifest.xml`, which the active engine locates.
 */
private class ManifestPermissionPlugin(private val env: ApplicationEnvironment) : Plugin {
    override val manifest = PluginManifest(
        id = "manifest-permission-fix",
        name = "Missing Permission Fix",
        description = "Adds the permission an Android API call needs to the module's AndroidManifest.xml.",
    )

    override fun register(reg: PluginRegistration) {
        reg.register(
            QUICK_FIX_PROVIDER_EP,
            com.shd.codestudio.core.analysis.ManifestPermissionFixProvider { module -> env.activeEngine?.manifestFileOf(module) },
        )
    }
}

/**
 * "Add library …" on an unresolved import, unknown class name or XML view tag (Java, Kotlin, XML): adds the
 * library that provides it to the module. See [com.shd.codestudio.core.analysis.MissingLibraryFixProvider].
 */
private class MissingLibraryPlugin(private val env: ApplicationEnvironment) : Plugin {
    override val manifest = PluginManifest(
        id = "missing-library-fix",
        name = "Missing Library Fix",
        description = "Offers to add the library an unresolved import, class or layout tag comes from.",
    )

    override fun register(reg: PluginRegistration) {
        reg.register(
            QUICK_FIX_PROVIDER_EP,
            com.shd.codestudio.core.analysis.MissingLibraryFixProvider(
                missing = { env.activeEngine?.dependencies?.missingLibraries },
                diagnosticsOf = { file -> env.activeEngine?.publishedDiagnostics(file).orEmpty() },
            ),
        )
    }
}

/**
 * The XML editor diagnostics, wired to the active engine's per-project resource host + Android attribute
 * schema + element catalog (all three resolve `env.activeEngine` lazily). Attributed to
 * `PluginId("xml-analysis")` by the facade.
 */
private class XmlAnalysisPlugin(private val env: ApplicationEnvironment) : Plugin {
    override val manifest =
        PluginManifest(
            id = "xml-analysis",
            name = "XML Analysis",
            description = "XML/Android resource diagnostics and quick-fixes (unresolved references and elements, hardcoded strings, missing attributes).",
            dependsOn = listOf("xml-language"),
        )

    override fun register(reg: PluginRegistration) {
        reg.contributeVia { ext, _ ->
            val metadata = { env.activeEngine?.sdkLayoutMetadata() ?: AndroidSdkMetadata.bundled() }
            XmlAnalysisSupport.register(
                ext,
                ActiveEngineXmlResourceHost(env),
                AndroidXmlChecker(layout = metadata),
                AndroidXmlTagChecker(
                    layout = metadata,
                    // Null (no active engine / cold index) keeps the unknown-element check silent.
                    projectViews = { path -> env.activeEngine?.layoutViewCatalog(Paths.get(path)) },
                    classExists = { path, fqn ->
                        env.activeEngine?.layoutClassExists(Paths.get(path), fqn) ?: true
                    },
                ),
            )
        }
    }
}

/** The Android app-compat XML intention (delegates the "uses appcompat?" check to the active engine). */
private class AndroidXmlPlugin(private val env: ApplicationEnvironment) : Plugin {
    override val manifest = PluginManifest(
        id = "android-xml", name = "Android XML",
        description = "Android XML intentions, such as the AppCompat migration action.",
    )
    override fun register(reg: PluginRegistration) {
        reg.register(
            ACTION_PROVIDER_EP,
            AndroidXmlActionProvider { target ->
                env.activeEngine?.moduleUsesAppCompat(target) ?: false
            },
        )
    }
}

/**
 * The engine's scoped services: the MODULE-scoped per-language analyzers and the WORKSPACE-scoped concern
 * services. Each factory resolves the per-project engine via [ENGINE_CONTEXT] (published on every engine's own
 * workspace container in `registerScopedServices`), so this single app-global registration serves every
 * opened project.
 */
private class IdeCoreServicesPlugin : Plugin {
    override val manifest = PluginManifest(
        id = "ide-core-services", name = "IDE Core Services", essential = true,
        description = "The engine's scoped services (analyzers, build, module, search, dependencies, signing).",
    )

    override fun register(reg: PluginRegistration) {
        // ONE module-scoped service for every language: it builds an analyzer per language on first use
        // through the backend registered for it, so adding a language is a LANGUAGE_BACKEND_EP registration
        // and nothing here changes.
        reg.service(MODULE_ANALYZERS, ServiceScopeLevel.MODULE) {
            val ctx = getService(ENGINE_CONTEXT)
            val module = module()
            ModuleAnalyzers { language -> ctx.buildAnalyzer(module, language) }
        }
        reg.service(SIGNING_SERVICE, ServiceScopeLevel.WORKSPACE) {
            SigningService(
                getService(
                    ENGINE_CONTEXT
                )
            )
        }
        reg.service(SEARCH_SERVICE, ServiceScopeLevel.WORKSPACE) {
            SearchService(
                getService(
                    ENGINE_CONTEXT
                )
            )
        }
        reg.service(BLOCK_SERVICE, ServiceScopeLevel.WORKSPACE) {
            BlockService(
                getService(
                    ENGINE_CONTEXT
                )
            )
        }
        reg.service(ACTION_MANAGER, ServiceScopeLevel.WORKSPACE) {
            ActionManager(getService(ENGINE_CONTEXT).platform.extensions)
        }
        reg.service(EDITOR_DECORATIONS, ServiceScopeLevel.WORKSPACE) {
            EditorDecorationCollector(getService(ENGINE_CONTEXT).platform.extensions)
        }
        reg.service(DEPENDENCY_SERVICE, ServiceScopeLevel.WORKSPACE) {
            DependencyService(
                getService(
                    ENGINE_CONTEXT
                )
            )
        }
        reg.service(MODULE_SERVICE, ServiceScopeLevel.WORKSPACE) {
            ModuleService(
                getService(
                    ENGINE_CONTEXT
                )
            )
        }
        reg.service(BUILD_SERVICE, ServiceScopeLevel.WORKSPACE) {
            BuildService(
                getService(
                    ENGINE_CONTEXT
                )
            )
        }
        // The same instances again, under the keys the PUBLISHED api modules declare, so an installed
        // plugin can NAME them: it compiles against build-api / index-api / project-model-api /
        // analysis-api, never against `:ide-core`. The published keys are typed to narrowed interfaces
        // (BuildControl, SymbolSearch, ModuleSources, ModuleAnalysis, ModuleResources), so only the promoted
        // members are frozen as plugin API and the rest of each service stays internal and free to change.
        // Each alias resolves the internal key, so a scope holds ONE instance however it is asked for.
        //
        // MODULE_RESOURCES sits beside its service rather than up here because the ordering matters for
        // nothing else: the alias resolves ANDROID_RESOURCE_SERVICE lazily, like every other one.
        reg.service(BUILD_CONTROL, ServiceScopeLevel.WORKSPACE) { getService(BUILD_SERVICE) }
        reg.service(SYMBOL_SEARCH, ServiceScopeLevel.WORKSPACE) { getService(SEARCH_SERVICE) }
        reg.service(MODULE_SOURCES, ServiceScopeLevel.WORKSPACE) { getService(MODULE_SERVICE) }
        reg.service(MODULE_ANALYSIS, ServiceScopeLevel.MODULE) { getService(MODULE_ANALYZERS) }
        reg.service(PROJECT_SYNC_SERVICE, ServiceScopeLevel.WORKSPACE) {
            ProjectSyncService(getService(ENGINE_CONTEXT))
        }
        reg.service(LANGUAGE_FEATURE_SERVICE, ServiceScopeLevel.WORKSPACE) {
            LanguageFeatureService(getService(ENGINE_CONTEXT))
        }
        reg.service(ANDROID_RESOURCE_SERVICE, ServiceScopeLevel.WORKSPACE) {
            AndroidResourceService(getService(ENGINE_CONTEXT))
        }
        reg.service(MODULE_RESOURCES, ServiceScopeLevel.WORKSPACE) { getService(ANDROID_RESOURCE_SERVICE) }
        reg.service(REFACTOR_SERVICE, ServiceScopeLevel.WORKSPACE) {
            RefactorService(getService(ENGINE_CONTEXT), getService(KOTLIN_EDITOR_SERVICE))
        }
        reg.service(APP_RENAME_SERVICE, ServiceScopeLevel.WORKSPACE) {
            AppRenameService(getService(ENGINE_CONTEXT))
        }
        reg.service(KOTLIN_EDITOR_SERVICE, ServiceScopeLevel.WORKSPACE) {
            KotlinEditorService(getService(ENGINE_CONTEXT))
        }
        reg.service(COMPOSE_PREVIEW_SERVICE, ServiceScopeLevel.WORKSPACE) {
            ComposePreviewService(getService(ENGINE_CONTEXT))
        }
        reg.service(ICON_MANAGER_SERVICE, ServiceScopeLevel.WORKSPACE) {
            IconManagerService(getService(ENGINE_CONTEXT))
        }
        reg.service(INTERPRETER_LOWERING, ServiceScopeLevel.WORKSPACE) {
            KotlinProgramLowering(getService(ENGINE_CONTEXT))
        }
    }
}

/**
 * The plugin-facing interpreter: `CODE_INTERPRETER`, through which an installed plugin runs the user's project
 * code: its own preview of a framework the IDE knows nothing about, or a framework's entry point (see
 * docs/plugin-interpreter.md).
 *
 * APPLICATION-scoped, unlike the engine services around it, because a plugin resolves services through
 * `PluginRegistration.appServices` and holds no project. It therefore follows whichever project is open, via
 * [ApplicationEnvironment.activeEngine], and answers `LowerResult.NotReady` when there is none, the same
 * shape as "still indexing", which is the honest answer for a caller that should retry rather than report.
 *
 * Non-essential on purpose: a plugin that needs it declares `dependsOn = ["interpreter"]`, so a user who
 * turns this off turns off what depends on it instead of leaving it half-working.
 */
private class InterpreterPlugin(private val env: ApplicationEnvironment) : Plugin {
    override val manifest = PluginManifest(
        id = "interpreter", name = "Interpreter",
        description = "Lets a plugin run the open project's code interpreted, for a preview or a run of its own.",
    )

    override fun register(reg: PluginRegistration) {
        reg.service(CODE_INTERPRETER, ServiceScopeLevel.APPLICATION) {
            CodeInterpreterImpl(
                lowering = { request ->
                    env.activeEngine?.interpreterLowering?.lower(request)
                        ?: LowerResult.NotReady("no project is open")
                },
                // A plugin that names no categories gets the ones the USER configured for previews, not a free
                // hand; with no project open there is nothing to read, so restrict everything.
                projectSandbox = { env.activeEngine?.composePreviewSandbox() ?: RESTRICT_ALL },
                appServices = container,
            )
        }
    }
}

/** The built-in command-palette actions (Run / Stop build, Re-index) that act on the active engine. */
private class IdeCoreActionsPlugin(private val env: ApplicationEnvironment) : Plugin {
    override val manifest = PluginManifest(
        id = "ide-core-actions", name = "IDE Core Actions",
        description = "Built-in command-palette actions: Run, Stop build, and Re-index.",
    )
    override fun register(reg: PluginRegistration) {
        reg.contributeVia { ext, _ -> BuiltInActions.register(ext, env) }
    }
}

/** The line-level editor actions (comment toggling, moving and sorting lines) plus move-to-a-new-file. */
private class EditorTextActionsPlugin : Plugin {
    override val manifest = PluginManifest(
        id = "editor-text-actions", name = "Editor Line Actions",
        description = "Editor actions on lines (comment, move, sort) and moving a declaration to its own file.",
    )
    override fun register(reg: PluginRegistration) {
        reg.contributeVia { ext, _ -> EditorTextActions.register(ext) }
        reg.contributeVia { ext, _ -> ExtractFileAction.register(ext) }
    }
}
