package com.shd.codestudio.android.support

import com.shd.codestudio.android.support.tools.AndroidSdk
import com.shd.codestudio.android.support.tools.DebugKeystore
import com.shd.codestudio.build.BuildGoal
import com.shd.codestudio.build.BuildRequest
import com.shd.codestudio.build.VariantSelector
import com.shd.codestudio.build.engine.BuildCache
import com.shd.codestudio.build.engine.SimpleTaskContext
import com.shd.codestudio.build.engine.TaskExecutorImpl
import com.shd.codestudio.model.BuildSystemId
import com.shd.codestudio.model.LanguageLevel
import com.shd.codestudio.model.ModuleId
import com.shd.codestudio.model.FacetCodecRegistry
import com.shd.codestudio.model.ModuleTypeRegistry
import com.shd.codestudio.model.impl.ProjectModel
import com.shd.codestudio.model.impl.ProjectModelStore
import com.shd.codestudio.platform.impl.PlatformCore
import com.shd.codestudio.testkit.testEnv
import com.shd.codestudio.testkit.withTempDir
import com.shd.codestudio.testkit.writeSource
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import com.shd.codestudio.model.impl.open

/**
 * Covers the on-device build wiring added for mobile assembly: [AndroidSdk.forDevice] (native tools
 * resolved from the app's `nativeLibraryDir` as `lib*.so`) and the in-process
 * [com.shd.codestudio.android.support.tools.ApksigSigner], which aligns + signs without a native `zipalign` binary.
 * The device app uses `AndroidBuildSystem.inProcess` over exactly such an `AndroidSdk`.
 */
class AndroidDeviceToolsTest {

    /** `forDevice` points aapt2/zipalign at `nativeLibraryDir/lib*.so` and reports readiness off those. */
    @Test
    fun forDeviceMapsNativeToolsIntoNativeLibDir() {
        withTempDir("device-sdk") { dir ->
            val nativeLibDir = dir.resolve("lib/arm64-v8a").also { Files.createDirectories(it) }
            val androidJar = dir.resolve("android.jar").also { Files.writeString(it, "stub") }
            val sdk = AndroidSdk.forDevice(androidJar, nativeLibDir)

            assertEquals(nativeLibDir.resolve("libaapt2.so"), sdk.aapt2)
            assertEquals(nativeLibDir.resolve("libzipalign.so"), sdk.zipalign)
            assertEquals(nativeLibDir, sdk.buildToolsDir)

            // No aapt2 on disk yet → not ready; create it → ready (only androidJar + aapt2 are required).
            assertFalse(sdk.hasNativeTools(), "should not be ready before libaapt2.so exists")
            Files.writeString(sdk.aapt2, "fake-elf")
            assertTrue(sdk.hasNativeTools(), "androidJar + libaapt2.so present → ready")
        }
    }

    /**
     * The on-device dex/sign path produces a valid signed APK with no native `zipalign` at all: build a real
     * one-module APK via `inProcess` (which wires [com.shd.codestudio.android.support.tools.ApksigSigner], aligning the
     * archive itself via apksig's `setAlignmentPreserved(false)`) and assert it is v1-signed. Also exercises
     * the legacy-PKCS12 debug keystore [DebugKeystore.getOrCreate] mints (the format Android's BouncyCastle
     * can read on-device). Skipped (not failed) without an installed SDK.
     */
    @Test
    fun inProcessSignsAndAlignsApkWithoutZipalign() {
        val detected = assumeAndroidSdk()

        testEnv("device-apk") { env ->
            val dir = env.dir
            val platform = env.platform
            val store = buildAppWorkspace(dir, platform)
            val project = store.workspace.projects.single()
            val signing = DebugKeystore.getOrCreate(dir.resolve(".keystore/debug.ks"), detected.keytool)

            // inProcess uses ApksigSigner(), which aligns + signs without any native zipalign binary.
            val buildSystem = AndroidBuildSystem.inProcess(detected, signing)
            val graph = buildSystem.createBuildGraph(
                project, BuildRequest(listOf(ModuleId("app")), VariantSelector("debug"), BuildGoal.PACKAGE),
            )
            val log = StringBuilder()
            val outcome = runBlocking {
                TaskExecutorImpl(BuildCache(dir.resolve(".caches/build")))
                    .execute(graph, SimpleTaskContext(log = { log.appendLine(it) }), 2)
            }
            assertTrue(outcome.succeeded, "APK build failed:\n$log")

            val apk = dir.resolve("app/build/outputs/apk/debug/app-debug.apk")
            assertTrue(Files.isRegularFile(apk), "signed APK missing at $apk\n$log")
            val entries = ZipFile(apk.toFile()).use { z -> z.entries().toList().map { it.name }.toSet() }
            assertTrue("classes.dex" in entries, "APK lacks classes.dex: $entries")
            assertTrue(entries.any { it.startsWith("META-INF/") && (it.endsWith(".RSA") || it.endsWith(".SF")) },
                "APK is not v1-signed: $entries")
        }
    }

    private fun buildAppWorkspace(dir: Path, platform: PlatformCore): ProjectModelStore {
        val store = ProjectModel.open(dir, platform, FacetCodecRegistry().register(AndroidFacetCodec))
        ModuleTypeRegistry(platform.extensions).register(AndroidAppModuleType, AndroidSupport.PLUGIN)
        val appType = ModuleTypeRegistry(platform.extensions).resolve("android-app")

        store.workspace.beginModification().apply { addProject("demo", BuildSystemId.NATIVE, store.vfs.root()); commit() }
        store.workspace.projects.single().beginModification().apply {
            addModule("app", appType).apply {
                languageLevel = LanguageLevel.JAVA_17
                putFacet(AndroidFacet(namespace = "com.example.app", compileSdk = 34, minSdk = 24, targetSdk = 34, isApplication = true))
            }
            commit()
        }
        dir.writeSource("app/src/main/AndroidManifest.xml", MANIFEST)
        dir.writeSource("app/src/main/res/values/strings.xml", STRINGS)
        dir.writeSource("app/src/main/java/com/example/app/MainActivity.java", ACTIVITY)
        return store
    }

    private companion object {
        val MANIFEST = """
            <?xml version="1.0" encoding="utf-8"?>
            <manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.example.app">
                <application android:label="@string/app_name">
                    <activity android:name=".MainActivity" android:exported="true"/>
                </application>
            </manifest>
        """

        val STRINGS = """
            <?xml version="1.0" encoding="utf-8"?>
            <resources>
                <string name="app_name">Code Studio Device Demo</string>
            </resources>
        """

        val ACTIVITY = """
            package com.example.app;

            import android.app.Activity;
            import android.os.Bundle;
            import android.widget.TextView;

            public class MainActivity extends Activity {
                @Override
                protected void onCreate(Bundle savedInstanceState) {
                    super.onCreate(savedInstanceState);
                    TextView tv = new TextView(this);
                    tv.setText(R.string.app_name);
                    setContentView(tv);
                }
            }
        """
    }
}
