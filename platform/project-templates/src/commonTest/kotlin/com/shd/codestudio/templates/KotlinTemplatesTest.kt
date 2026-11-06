package com.shd.codestudio.templates

import com.shd.codestudio.model.FacetCodecRegistry
import com.shd.codestudio.model.LanguageLevel
import com.shd.codestudio.model.ModuleTypeRegistry
import com.shd.codestudio.model.impl.ModelPersistence
import com.shd.codestudio.model.impl.ProjectModel
import com.shd.codestudio.model.impl.ProjectModelStore
import com.shd.codestudio.model.impl.StoreScaffold
import com.shd.codestudio.model.template.ProjectTemplate
import com.shd.codestudio.model.template.TemplateArgs
import com.shd.codestudio.platform.PluginId
import com.shd.codestudio.platform.impl.PlatformCore
import com.shd.codestudio.platform.readFile
import com.shd.codestudio.platform.resolvePath
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The built-in templates, on whatever platform this runs on.
 *
 * It is not a duplicate of the hosts' own suites: those assert that creating a project through a BACKEND
 * works. This asserts what a template itself produces -- the module, its source set, the file and the path
 * it lands at -- which is the contract both hosts depend on and neither one owns.
 */
class KotlinTemplatesTest {

    private val dir = scratchDir("project-templates")

    @AfterTest
    fun cleanUp() = deleteTree(dir)

    private fun generate(template: ProjectTemplate, name: String, pkg: String): ProjectModelStore {
        val platform = PlatformCore()
        ModuleTypeRegistry(platform.extensions).register(JavaLibModuleType, PluginId("test"))
        val store = ProjectModel.open(dir, platform, FacetCodecRegistry())
        template.generate(
            StoreScaffold(store, LanguageLevel.JAVA_17),
            TemplateArgs(mapOf(TemplateArgs.NAME to name, TemplateArgs.PACKAGE to pkg)),
        )
        store.save()
        return store
    }

    @Test
    fun theConsoleTemplateScaffoldsAModuleAndAnEntryPoint() {
        val store = generate(KotlinConsoleAppTemplate, "My App", "com.example.demo")
        try {
            val project = store.workspace.projects.single()
            assertEquals("My App", project.name)

            val module = project.modules.single()
            assertEquals("app", module.name)
            assertEquals("java-lib", module.type.id)
            assertEquals(LanguageLevel.JAVA_17, module.languageLevel)

            // The source set is what makes the tree below it SOURCE rather than just files on disk.
            val sourceSet = module.sourceSets.single()
            assertEquals("main", sourceSet.name)
            assertTrue(
                sourceSet.contentRoots.any { it.dir.path.endsWith("src/main/kotlin") },
                sourceSet.contentRoots.map { it.dir.path }.toString(),
            )

            val main = resolvePath(dir, "app/src/main/kotlin/com/example/demo/Main.kt")
            val text = assertNotNull(readFile(main), "expected an entry point at $main").decodeToString()
            assertTrue(text.startsWith("package com.example.demo\n"), text)
            assertTrue(text.contains("fun main()"), text)
            // `writeText` trims the template's own indentation, or every generated file starts indented.
            assertTrue(text.lines().none { it.startsWith("    package") }, text)
        } finally {
            store.close()
        }
    }

    @Test
    fun theLibraryTemplateNamesItsClassAfterTheProject() {
        val store = generate(KotlinLibraryTemplate, "my cool lib", "com.example")
        try {
            assertEquals("lib", store.workspace.projects.single().modules.single().name)
            // "my cool lib" is not a type name; the separators are dropped and each word capitalised.
            val source = resolvePath(dir, "lib/src/main/kotlin/com/example/MyCoolLib.kt")
            val text = assertNotNull(readFile(source), "expected $source").decodeToString()
            assertTrue(text.contains("class MyCoolLib"), text)
            assertTrue(!text.contains("fun main("), "a library has no entry point: $text")
        } finally {
            store.close()
        }
    }

    /** What the scaffold wrote has to be READABLE as a model, not just present in memory. */
    @Test
    fun theGeneratedProjectReadsBackAsAModel() {
        generate(KotlinConsoleAppTemplate, "Roundtrip", "com.example").close()

        assertTrue(ModelPersistence.exists(dir))
        val reloaded = ModelPersistence.load(dir)
        assertEquals(listOf("Roundtrip"), reloaded.projects.map { it.name })
        assertEquals(listOf("app"), reloaded.projects.single().modules.map { it.name })
    }

    @Test
    fun aTypeNameIsAlwaysAValidIdentifier() {
        assertEquals("App", TemplateSupport.typeName(""))
        assertEquals("App", TemplateSupport.typeName("!!!"))
        // A name that starts with a digit is not an identifier, so it is prefixed rather than rejected.
        assertEquals("App2048", TemplateSupport.typeName("2048"))
        assertEquals("MyApp", TemplateSupport.typeName("my-app"))
    }
}
