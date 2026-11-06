package com.shd.codestudio.android.support

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
import com.shd.codestudio.testkit.testEnv
import com.shd.codestudio.testkit.writeSource
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue
import com.shd.codestudio.model.impl.open

/**
 * Regression for the "introduce a resource error, then revert, and the build is stuck" report. Builds an
 * APK, breaks `styles.xml` (aapt2 fails), builds (must fail), reverts the edit, and builds again over the
 * SAME build cache — which must recover and succeed.
 */
class AndroidIncrementalResourceTest {

    @Test
    fun revertingAResourceErrorRecoversTheBuild() {
        val sdk = assumeAndroidSdk()

        testEnv("android-incres") { env ->
            val dir = env.dir
            val platform = env.platform
            val store = ProjectModel.open(dir, platform, FacetCodecRegistry().register(AndroidFacetCodec))
            ModuleTypeRegistry(platform.extensions).register(AndroidAppModuleType, AndroidSupport.PLUGIN)
            val appType = ModuleTypeRegistry(platform.extensions).resolve("android-app")
            store.workspace.beginModification().apply { addProject("demo", BuildSystemId.NATIVE, store.vfs.root()); commit() }
            store.workspace.projects.single().beginModification().apply {
                addModule("app", appType).apply {
                    languageLevel = LanguageLevel.JAVA_17
                    putFacet(AndroidFacet(namespace = "com.example.app", compileSdk = 34, minSdk = 24, targetSdk = 34))
                }
                commit()
            }
            dir.writeSource("app/src/main/AndroidManifest.xml", MANIFEST)
            dir.writeSource("app/src/main/res/values/strings.xml", STRINGS)
            dir.writeSource("app/src/main/res/values/styles.xml", GOOD_STYLES)
            dir.writeSource("app/src/main/java/com/example/app/MainActivity.java", ACTIVITY)

            val signing = DebugKeystore.getOrCreate(dir.resolve(".keystore/debug.ks"), sdk.keytool)
            val buildSystem = AndroidBuildSystem.subprocess(sdk, signing) // the wiring the IDE uses
            val cache = BuildCache(dir.resolve(".caches/build"))
            fun build(): Boolean {
                val graph = buildSystem.createBuildGraph(
                    store.workspace.projects.single(),
                    BuildRequest(listOf(ModuleId("app")), VariantSelector("debug"), BuildGoal.PACKAGE),
                )
                return runBlocking { TaskExecutorImpl(cache).execute(graph, SimpleTaskContext(), 2) }.succeeded
            }

            assertTrue(build(), "initial build should succeed")

            dir.writeSource("app/src/main/res/values/styles.xml", BROKEN_STYLES) // references a missing color
            assertTrue(!build(), "build with the broken resource must fail")

            dir.writeSource("app/src/main/res/values/styles.xml", GOOD_STYLES) // revert
            assertTrue(build(), "reverting the resource error must let the build recover (not stay stuck)")
        }
    }

    private companion object {
        val MANIFEST = """
            <?xml version="1.0" encoding="utf-8"?>
            <manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.example.app">
                <application android:label="@string/app_name"><activity android:name=".MainActivity" android:exported="true"/></application>
            </manifest>
        """
        val STRINGS = """
            <?xml version="1.0" encoding="utf-8"?>
            <resources><string name="app_name">Inc Res</string></resources>
        """
        val GOOD_STYLES = """
            <?xml version="1.0" encoding="utf-8"?>
            <resources>
                <color name="primary">#FF0000FF</color>
                <style name="Theme.App" parent="android:Theme.Material.Light">
                    <item name="android:colorPrimary">@color/primary</item>
                </style>
            </resources>
        """
        // References @color/missing — aapt2 link fails on the undefined resource.
        val BROKEN_STYLES = """
            <?xml version="1.0" encoding="utf-8"?>
            <resources>
                <style name="Theme.App" parent="android:Theme.Material.Light">
                    <item name="android:colorPrimary">@color/missing</item>
                </style>
            </resources>
        """
        val ACTIVITY = """
            package com.example.app;
            import android.app.Activity;
            import android.os.Bundle;
            public class MainActivity extends Activity {
                @Override protected void onCreate(Bundle b) { super.onCreate(b); }
            }
        """
    }
}
