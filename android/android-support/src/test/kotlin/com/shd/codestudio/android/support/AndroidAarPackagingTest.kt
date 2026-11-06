package com.shd.codestudio.android.support

import com.shd.codestudio.android.support.tools.AarMetadata
import com.shd.codestudio.android.support.tools.DebugKeystore
import com.shd.codestudio.build.BuildGoal
import com.shd.codestudio.build.BuildRequest
import com.shd.codestudio.build.VariantSelector
import com.shd.codestudio.build.engine.BuildCache
import com.shd.codestudio.build.engine.SimpleTaskContext
import com.shd.codestudio.build.engine.TaskExecutorImpl
import com.shd.codestudio.model.BuildSystemId
import com.shd.codestudio.model.FacetTemplate
import com.shd.codestudio.model.ModuleId
import com.shd.codestudio.model.ModuleType
import com.shd.codestudio.model.SourceSetTemplate
import com.shd.codestudio.model.FacetCodecRegistry
import com.shd.codestudio.model.ModuleTypeRegistry
import com.shd.codestudio.model.impl.ProjectModel
import com.shd.codestudio.platform.PluginId
import com.shd.codestudio.testkit.testEnv
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import kotlin.test.Test
import kotlin.test.assertTrue
import com.shd.codestudio.model.impl.open

/**
 * `assembleAar` for an `android-lib` target packages a spec-faithful `.aar`: `classes.jar` of the library's
 * own code, its `AndroidManifest.xml`, the raw `res/`, the `R.txt` symbol table, and the AGP
 * `aar-metadata.properties`. Uses the multi-module sample's `feature` library. Skipped when no SDK.
 */
class AndroidAarPackagingTest {

    private object JavaLib : ModuleType {
        override val id = "java-lib"
        override val displayName = "Java Library"
        override fun defaultSourceSets(): List<SourceSetTemplate> = emptyList()
        override fun defaultFacets(): List<FacetTemplate> = emptyList()
        override fun supportedBuildSystems(): Set<BuildSystemId> = setOf(BuildSystemId.NATIVE)
    }

    @Test
    fun theAndroidLibraryAssemblesToAnAar() {
        val sdk = assumeAndroidSdk()

        testEnv("android-aar") { env ->
            val dir = env.dir
            val platform = env.platform
            val store = ProjectModel.open(dir, platform, FacetCodecRegistry().register(AndroidFacetCodec))
            val types = ModuleTypeRegistry(platform.extensions)
            types.register(JavaLib, PluginId("java-support"))
            AndroidSupport.register(types, FacetCodecRegistry())

            SampleAndroidProject.generate(store, types.resolve("android-app"), types.resolve("android-lib"), types.resolve("java-lib"))

            val signing = DebugKeystore.getOrCreate(dir.resolve(".keystore/debug.ks"), sdk.keytool)
            val buildSystem = AndroidBuildSystem.inProcess(sdk, signing)
            val project = store.workspace.projects.single { it.name == SampleAndroidProject.PROJECT }

            // Assemble the android-lib TARGET (not the app) → an .aar.
            val graph = buildSystem.createBuildGraph(
                project, BuildRequest(listOf(ModuleId("feature")), VariantSelector("debug"), BuildGoal.ASSEMBLE),
            )
            val log = StringBuilder()
            val outcome = runBlocking {
                TaskExecutorImpl(BuildCache(dir.resolve(".caches/build"))).execute(graph, SimpleTaskContext(log = { log.appendLine(it) }), 2)
            }
            assertTrue(outcome.succeeded, "android-lib AAR assembly failed:\n$log")

            val aar = dir.resolve("feature/build/outputs/aar/feature-debug.aar")
            assertTrue(Files.isRegularFile(aar), "AAR not produced at $aar\n$log")
            ZipFile(aar.toFile()).use { zf ->
                val entries = zf.entries().toList().map { it.name }.toSet()
                assertTrue("classes.jar" in entries, "AAR missing classes.jar: $entries")
                assertTrue("AndroidManifest.xml" in entries, "AAR missing AndroidManifest.xml: $entries")
                assertTrue("R.txt" in entries, "AAR missing R.txt: $entries")
                assertTrue(entries.any { it.startsWith("res/") }, "AAR missing res/: $entries")
                assertTrue(AarMetadata.ENTRY_PATH in entries, "AAR missing aar-metadata.properties: $entries")

                // The library's own class is inside classes.jar (and the compile-only R is NOT).
                val innerJar = zf.getInputStream(zf.getEntry("classes.jar")).readBytes()
                val innerEntries = ZipInputStream(innerJar.inputStream()).use { zis ->
                    generateSequence { zis.nextEntry }.map { it.name }.toList()
                }
                assertTrue(innerEntries.any { it.contains("com/example/feature/FeatureText") }, "classes.jar missing FeatureText: $innerEntries")

                // R.txt lists the library's own resource symbols.
                val rtxt = zf.getInputStream(zf.getEntry("R.txt")).readBytes().toString(Charsets.UTF_8)
                assertTrue("feature_title" in rtxt, "R.txt should list the lib's resources: $rtxt")
            }
        }
    }
}
