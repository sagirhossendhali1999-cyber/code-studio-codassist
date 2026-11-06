package com.shd.codestudio.core

import com.shd.codestudio.core.gradle.GradleModelImporter
import com.shd.codestudio.core.gradle.GradleProjectImporter
import com.shd.codestudio.model.BuildSystemId
import com.shd.codestudio.model.ContentRole
import com.shd.codestudio.model.DependencyScope
import com.shd.codestudio.model.sync.ExternalLibrary
import com.shd.codestudio.model.sync.ExternalModuleRef
import com.shd.codestudio.model.sync.ExternalPlatform
import com.shd.codestudio.model.sync.SyncReason
import com.shd.codestudio.model.sync.SyncRequest
import com.shd.codestudio.model.sync.SyncSeverity
import com.shd.codestudio.platform.ProgressReporter
import com.shd.codestudio.testkit.withTempDir
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The exported-model import path: what `./gradlew generateNativeModel` writes, read back into a project
 * model. The shapes exercised here are the ones Code Studio's own repository has and the script-reading
 * importer cannot represent — a module directory that does not follow from its Gradle path, and a
 * multiplatform module with several source sets.
 */
class GradleModelImportTest {

    private fun write(dir: Path, json: String) {
        val f = dir.resolve(GradleModelImporter.DUMP_PATH)
        Files.createDirectories(f.parent)
        f.writeText(json.trimIndent())
    }

    private fun resolve(dir: Path) = runBlocking {
        GradleModelImporter().resolve(SyncRequest(dir, NoProgress, SyncReason.IMPORT))
    }

    private object NoProgress : ProgressReporter {
        override fun report(fraction: Double, message: String?) {}
        override fun checkCanceled() {}
        override val isCanceled = false
    }

    @Test
    fun readsModulesWhoseDirectoryDoesNotFollowFromTheirGradlePath() {
        withTempDir("gradle-model-layers") { dir ->
            // `:platform-core` living at `platform/platform-core` is exactly the remapping that makes this
            // repository unreadable to a path-deriving importer.
            write(dir, """
                {
                  "version": 1,
                  "name": "codestudio",
                  "target": "android",
                  "repositories": [{ "name": "Google", "url": "https://dl.google.com/dl/android/maven2/" }],
                  "modules": [
                    {
                      "name": "platform-core",
                      "dir": "platform/platform-core",
                      "type": "java-lib",
                      "multiplatform": false,
                      "sourceSets": [
                        {
                          "name": "main",
                          "scope": "IMPLEMENTATION",
                          "roots": { "src/main/kotlin": ["source"], "src/main/resources": ["resource"] }
                        }
                      ],
                      "dependencies": [
                        { "kind": "library", "coordinate": "org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0", "scope": "IMPLEMENTATION" },
                        { "kind": "platform", "coordinate": "org.junit:junit-bom:5.11.4", "scope": "TEST_IMPLEMENTATION" },
                        { "kind": "module", "name": "test-support", "scope": "TEST_IMPLEMENTATION" }
                      ]
                    }
                  ]
                }
            """)

            val outcome = resolve(dir)
            val model = assertNotNull(outcome.model)
            assertEquals(BuildSystemId.NATIVE, model.buildSystemId, "an imported model is built natively")
            assertEquals("codestudio", model.name)

            val module = model.modules.single()
            assertEquals("platform/platform-core", module.dirRelPath)
            assertEquals("java-lib", module.typeId)

            val main = module.sourceSets.single()
            assertEquals(setOf(ContentRole.SOURCE), main.roots["src/main/kotlin"])
            assertEquals(setOf(ContentRole.RESOURCE), main.roots["src/main/resources"])

            val library = module.dependencies.filterIsInstance<ExternalLibrary>().single()
            assertEquals("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0", library.coordinate)
            assertEquals(DependencyScope.IMPLEMENTATION, library.scope)
            val bom = module.dependencies.filterIsInstance<ExternalPlatform>().single()
            assertEquals("org.junit:junit-bom:5.11.4", bom.bom.toString())
            val moduleRef = module.dependencies.filterIsInstance<ExternalModuleRef>().single()
            assertEquals("test-support", moduleRef.moduleName)
            assertEquals(DependencyScope.TEST_IMPLEMENTATION, moduleRef.scope)

            assertEquals("https://dl.google.com/dl/android/maven2/", model.repositories.single().url)
        }
    }

    @Test
    fun foldsEverySourceSetOfAMultiplatformAndroidModuleIntoMain() {
        withTempDir("gradle-model-kmp") { dir ->
            write(dir, """
                {
                  "version": 1,
                  "name": "codestudio",
                  "target": "android",
                  "repositories": [],
                  "modules": [
                    {
                      "name": "ide-ui",
                      "dir": "app/ide-ui",
                      "type": "android-lib",
                      "multiplatform": true,
                      "sourceSets": [
                        { "name": "commonMain", "scope": "IMPLEMENTATION", "roots": { "src/commonMain/kotlin": ["source"] } },
                        { "name": "jvmShared", "scope": "IMPLEMENTATION", "roots": { "src/jvmShared/kotlin": ["source"] } },
                        { "name": "androidMain", "scope": "IMPLEMENTATION", "roots": { "src/androidMain/kotlin": ["source"] } }
                      ],
                      "dependencies": [
                        { "kind": "library", "coordinate": "org.jetbrains.compose.material3:material3:1.9.0", "scope": "API" }
                      ],
                      "android": {
                        "namespace": "com.shd.codestudio.ui",
                        "compileSdk": 36,
                        "minSdk": 24,
                        "targetSdk": 36,
                        "isApplication": false,
                        "buildFeatures": { "compose": true, "parcelize": false, "serialization": false }
                      }
                    }
                  ]
                }
            """)

            Files.createDirectories(dir.resolve("app/ide-ui/src/androidMain"))
            Files.writeString(dir.resolve("app/ide-ui/src/androidMain/AndroidManifest.xml"), "<manifest/>")

            val module = assertNotNull(resolve(dir).model).modules.single()
            // Folded into `main`, the only name an Android variant compiles, with every root in order.
            assertEquals(listOf("main"), module.sourceSets.map { it.name })
            assertEquals(
                listOf("src/commonMain/kotlin", "src/jvmShared/kotlin", "src/androidMain/kotlin"),
                module.sourceSets.single().roots.keys.toList(),
                "every source set of the exported target is declared, in order",
            )
            val android = module.facets.single { it.table == "android" }
            assertEquals("com.shd.codestudio.ui", android.values["namespace"])
            assertEquals(false, android.values["isApplication"])
            assertEquals("src/androidMain/AndroidManifest.xml", android.values["manifest"])
        }
    }

    /** An applicationId that differs from the namespace is carried by the facet, not swapped for the namespace. */
    @Test
    fun carriesAnApplicationIdThatDiffersFromTheNamespace() {
        withTempDir("gradle-model-appid") { dir ->
            write(dir, """
                {
                  "version": 1,
                  "name": "codestudio",
                  "target": "android",
                  "repositories": [],
                  "modules": [
                    {
                      "name": "ide-android",
                      "dir": "app/ide-android",
                      "type": "android-app",
                      "multiplatform": false,
                      "sourceSets": [],
                      "dependencies": [],
                      "android": {
                        "namespace": "com.shd.codestudio.android",
                        "compileSdk": 36,
                        "minSdk": 26,
                        "targetSdk": 36,
                        "isApplication": true,
                        "versionCode": 93,
                        "versionName": "3.18.2",
                        "applicationId": "com.sagirhossendhali1999-cyber.code"
                      }
                    }
                  ]
                }
            """)

            val outcome = resolve(dir)
            val android = assertNotNull(outcome.model).modules.single().facets.single()
            // The codec writes TOML integers, which are Long.
            assertEquals(26L, android.values["minSdk"])
            assertEquals(93L, android.values["versionCode"])
            assertEquals("com.shd.codestudio.android", android.values["namespace"])
            assertEquals("com.sagirhossendhali1999-cyber.code", android.values["applicationId"])
            assertTrue(outcome.messages.none { it.severity == SyncSeverity.WARNING }, "${outcome.messages}")
        }
    }

    @Test
    fun refusesADumpFromANewerBuild() {
        withTempDir("gradle-model-newer") { dir ->
            write(dir, """{ "version": 99, "name": "x", "modules": [] }""")
            val outcome = resolve(dir)
            assertNull(outcome.model)
            assertContains(outcome.messages.single().text, "newer than this build understands")
        }
    }

    /** With both importers applicable, the exported model wins: it is what Gradle actually configured. */
    @Test
    fun outranksTheScriptReadingImporter() {
        withTempDir("gradle-model-precedence") { dir ->
            dir.resolve("settings.gradle.kts").writeText("include(\":app\")")
            write(dir, """{ "version": 1, "name": "exported", "modules": [] }""")

            val exported = assertNotNull(GradleModelImporter().detect(dir))
            val scripts = assertNotNull(GradleProjectImporter().detect(dir))
            assertTrue(
                exported.confidence > scripts.confidence,
                "exported ${exported.confidence} should outrank scripts ${scripts.confidence}",
            )
            assertEquals("exported", exported.name)
        }
    }
}
