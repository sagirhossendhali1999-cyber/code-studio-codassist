package com.shd.codestudio.android.support

import com.shd.codestudio.android.support.templates.JetpackComposeAppTemplate
import com.shd.codestudio.model.FacetCodecRegistry
import com.shd.codestudio.model.LanguageLevel
import com.shd.codestudio.model.ModuleType
import com.shd.codestudio.model.ModuleTypeRegistry
import com.shd.codestudio.model.Workspace
import com.shd.codestudio.model.impl.ProjectModel
import com.shd.codestudio.model.impl.ProjectModelStore
import com.shd.codestudio.model.template.ProjectScaffold
import com.shd.codestudio.model.template.TemplateArgs
import com.shd.codestudio.testkit.testEnv
import com.shd.codestudio.vfs.VirtualFile
import java.nio.file.Files
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import com.shd.codestudio.model.impl.rootPath
import com.shd.codestudio.model.impl.open

class ComposeTemplateTest {

    @Test
    fun generatesProguardRulesFileAtTheModuleRoot() {
        withScaffold { scaffold, root ->
            JetpackComposeAppTemplate.generate(scaffold, args())
            // The default `release` build type references `proguard-rules.pro`; like the other Android
            // templates, the Compose template writes it so the entry resolves and the file is visible in the
            // tree (the curated Project view lists every module-root file).
            val rules = root.resolve("app/proguard-rules.pro")
            assertTrue(Files.isRegularFile(rules), "proguard-rules.pro is generated at the module root")
            assertEquals(
                "proguard-rules.pro",
                AndroidFacet.DEFAULT_BUILD_TYPES.first { it.name == "release" }.proguardFiles
                    .first { !DefaultProguardFiles.isDefault(it) },
                "the file matches the release build type's module-relative proguardFiles entry",
            )
        }
    }

    /** targetSdk 35+ is edge to edge: the starter screen must keep its content out from under the system bars. */
    @Test
    fun theStarterScreenPadsForTheSystemBars() {
        withScaffold { scaffold, root ->
            JetpackComposeAppTemplate.generate(scaffold, args())
            val main = Files.readString(root.resolve("app/src/main/kotlin/com/example/app/MainActivity.kt"))
            assertTrue("Modifier.safeDrawingPadding()" in main, main)
            assertTrue("enableEdgeToEdge()" in main && "import androidx.activity.enableEdgeToEdge" in main, "readable bar icons")
            assertTrue("import androidx.compose.foundation.layout.safeDrawingPadding" in main)
            assertTrue("import androidx.compose.ui.Modifier" in main)
        }
    }

    // --- support ---

    private fun args(extra: Map<String, String> = emptyMap()) = TemplateArgs(
        mapOf(TemplateArgs.NAME to "ComposeApp", TemplateArgs.PACKAGE to "com.example.app") + extra,
    )

    private fun withScaffold(body: (ProjectScaffold, root: java.nio.file.Path) -> Unit) = testEnv("compose-template") { env ->
        val dir = env.dir
        val platform = env.platform
        val store: ProjectModelStore = ProjectModel.open(dir, platform, FacetCodecRegistry().register(AndroidFacetCodec))
        ModuleTypeRegistry(platform.extensions).register(AndroidAppModuleType, AndroidSupport.PLUGIN)
        val scaffold = object : ProjectScaffold {
            override val workspace: Workspace get() = store.workspace
            override val rootDir: VirtualFile get() = store.vfs.root()
            override val languageLevel: LanguageLevel = LanguageLevel.JAVA_17
            override fun moduleType(id: String): ModuleType = ModuleTypeRegistry(platform.extensions).resolve(id)
            override fun writeText(relPath: String, content: String) {
                val file = store.rootPath.resolve(relPath)
                Files.createDirectories(file.parent)
                file.writeText(content.trimIndent() + "\n")
            }
            override fun writeBytes(relPath: String, bytes: ByteArray) {
                val file = store.rootPath.resolve(relPath)
                Files.createDirectories(file.parent)
                Files.write(file, bytes)
            }
        }
        body(scaffold, store.rootPath)
    }
}
