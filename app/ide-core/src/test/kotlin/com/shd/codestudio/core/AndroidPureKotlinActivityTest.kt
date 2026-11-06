package com.shd.codestudio.core

import com.shd.codestudio.android.support.AndroidAppModuleType
import com.shd.codestudio.android.support.AndroidBuildSystem
import com.shd.codestudio.android.support.AndroidFacet
import com.shd.codestudio.android.support.AndroidFacetCodec
import com.shd.codestudio.android.support.AndroidSupport
import com.shd.codestudio.android.support.tools.AndroidSdk
import com.shd.codestudio.android.support.tools.DebugKeystore
import com.shd.codestudio.build.BuildGoal
import com.shd.codestudio.build.BuildRequest
import com.shd.codestudio.build.VariantSelector
import com.shd.codestudio.build.engine.BuildCache
import com.shd.codestudio.build.engine.SimpleTaskContext
import com.shd.codestudio.build.engine.TaskExecutorImpl
import com.shd.codestudio.lang.kotlin.compile.IncrementalKotlinCompiler
import com.shd.codestudio.lang.kotlin.compile.KotlinJvmCompiler
import com.shd.codestudio.model.BuildSystemId
import com.shd.codestudio.model.DependencyScope
import com.shd.codestudio.model.LanguageLevel
import com.shd.codestudio.model.LibraryDependency
import com.shd.codestudio.model.LibraryKind
import com.shd.codestudio.model.LibraryRef
import com.shd.codestudio.model.ModuleId
import com.shd.codestudio.model.FacetCodecRegistry
import com.shd.codestudio.model.ModuleTypeRegistry
import com.shd.codestudio.model.impl.ProjectModel
import com.shd.codestudio.testkit.TestJars
import com.shd.codestudio.testkit.testEnv
import com.shd.codestudio.testkit.writeSource
import com.shd.codestudio.vfs.local.fileFor
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipFile
import kotlin.test.Test
import kotlin.test.assertTrue
import com.shd.codestudio.model.impl.open

/**
 * Reproduces the Compose-template shape: a **pure-Kotlin** android-app whose entry `MainActivity` is a
 * `.kt` file and which has **zero `.java` sources**. The device failure was a launch-time
 * `ClassNotFoundException` for the activity — i.e. the Kotlin activity never made it into the dex/APK.
 * This asserts the Kotlin activity is both dexed (project scope) and present in `classes.dex` of the APK.
 * Needs an installed SDK; skipped otherwise.
 */
class AndroidPureKotlinActivityTest {

    @Test
    fun pureKotlinActivityIsDexedAndPackaged() {
        val sdk = AndroidSdk.findSdkRoot()?.let { AndroidSdk.detect(it) }
        assumeTrue(sdk != null && sdk.isComplete(), "Android SDK not installed; skipping")
        sdk!!

        testEnv("android-pure-kotlin") { env ->
            val dir = env.dir
            val platform = env.platform
            val store = ProjectModel.open(dir, platform, FacetCodecRegistry().register(AndroidFacetCodec))
            ModuleTypeRegistry(platform.extensions).register(AndroidAppModuleType, AndroidSupport.PLUGIN)
            val appType = ModuleTypeRegistry(platform.extensions).resolve("android-app")
            store.workspace.beginModification().apply { addProject("demo", BuildSystemId.NATIVE, store.vfs.root()); commit() }
            store.workspace.libraryTable.create("kotlin-stdlib")
                .apply { kind = LibraryKind.JAR; addClassesRoot(store.vfs.fileFor(TestJars.kotlinStdlib())); commit() }
            store.workspace.projects.single().beginModification().apply {
                addModule("app", appType).apply {
                    languageLevel = LanguageLevel.JAVA_8
                    putFacet(AndroidFacet(namespace = "com.example.app", compileSdk = 34, minSdk = 24, targetSdk = 34))
                    addDependency(LibraryDependency(LibraryRef("kotlin-stdlib"), DependencyScope.IMPLEMENTATION))
                }
                commit()
            }
            dir.writeSource("app/src/main/AndroidManifest.xml", MANIFEST)
            dir.writeSource("app/src/main/res/values/strings.xml", STRINGS)
            // No .java anywhere — the activity itself is Kotlin (the Compose-template shape).
            dir.writeSource("app/src/main/kotlin/com/example/app/MainActivity.kt", ACTIVITY)

            val signing = DebugKeystore.getOrCreate(dir.resolve(".keystore/debug.ks"), sdk.keytool)
            val build = AndroidBuildSystem.subprocess(sdk, signing, kotlin = IncrementalKotlinCompiler(KotlinJvmCompiler()))
            val request = BuildRequest(listOf(ModuleId("app")), VariantSelector("debug"), BuildGoal.PACKAGE)
            val log = StringBuilder()
            val outcome = runBlocking {
                TaskExecutorImpl(BuildCache(dir.resolve(".caches/build")))
                    .execute(build.createBuildGraph(store.workspace.projects.single(), request), SimpleTaskContext(log = { log.appendLine(it) }), 2)
            }
            assertTrue(outcome.succeeded, "pure-kotlin android build failed:\n$log")

            val ran = outcome.ranTasks.map { it.value }
            assertTrue(":app:compileKotlinDebug" in ran, "compileKotlin must run for a pure-Kotlin app: $ran\n$log")

            val activityDex = dir.resolve("app/build/intermediates/android/debug/dex-archives/project/com/example/app/MainActivity.dex")
            assertTrue(Files.isRegularFile(activityDex), "Kotlin activity not dexed into project scope: $activityDex\n$log")

            val apk = AndroidBuildSystem.signedApkPath(store.workspace.projects.single().modules.single(), "debug")
            assertTrue(Files.isRegularFile(apk), "signed APK missing: $apk\n$log")
            assertTrue(apkHasDex(apk), "APK has no classes.dex: $apk\n$log")
        }
    }

    private fun apkHasDex(apk: Path): Boolean = ZipFile(apk.toFile()).use { zf ->
        zf.entries().asSequence().any { it.name.matches(Regex("classes\\d*\\.dex")) }
    }

    private companion object {
        val ACTIVITY = """
            package com.example.app
            import android.app.Activity
            import android.os.Bundle
            import android.widget.TextView
            class MainActivity : Activity() {
                override fun onCreate(b: Bundle?) {
                    super.onCreate(b)
                    val tv = TextView(this)
                    tv.text = "hello from kotlin activity"
                    setContentView(tv)
                }
            }
        """
        val MANIFEST = """
            <?xml version="1.0" encoding="utf-8"?>
            <manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.example.app">
                <application android:label="@string/app_name"><activity android:name=".MainActivity" android:exported="true"/></application>
            </manifest>
        """
        val STRINGS = """
            <?xml version="1.0" encoding="utf-8"?>
            <resources><string name="app_name">KotlinApp</string></resources>
        """
    }
}
