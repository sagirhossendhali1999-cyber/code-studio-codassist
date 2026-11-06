package com.shd.codestudio.android.support

import com.shd.codestudio.android.support.tasks.ApkPackaging
import com.shd.codestudio.android.support.tools.D8InProcessDexer
import com.shd.codestudio.android.support.tools.DebugKeystore
import com.shd.codestudio.android.support.tools.Dexer
import com.shd.codestudio.build.BuildGoal
import com.shd.codestudio.build.BuildRequest
import com.shd.codestudio.build.VariantSelector
import com.shd.codestudio.build.engine.BuildCache
import com.shd.codestudio.build.engine.SimpleTaskContext
import com.shd.codestudio.build.engine.TaskExecutorImpl
import com.shd.codestudio.lang.jdt.compile.JdtBatchCompiler
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
import com.shd.codestudio.testkit.testEnv
import com.shd.codestudio.testkit.writeSource
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue
import com.shd.codestudio.model.impl.open
import com.shd.codestudio.vfs.local.fileFor

/**
 * Proves the dexing is incremental: one `dexBuilder` archives every scope into content-addressed buckets,
 * so editing the app re-archives only its own classes (leaving the library's bucket untouched) and the
 * cheap `mergeProjectDex` re-runs, while the unchanged external-library layer (`mergeExtDex`) is skipped
 * (up-to-date), so the library is not re-dexed. Without per-input archiving a single edit would re-dex all.
 */
class AndroidIncrementalDexTest {

    @Test
    fun editingTheAppReDexesOnlyTheAppNotTheLibrary() {
        val sdk = assumeAndroidSdk()

        testEnv("android-incdex") { env ->
            val dir = env.dir
            val platform = env.platform
            val jarLib = compileJar(dir.resolve("jarlib"), sdk.androidJar)
            val store = ProjectModel.open(dir, platform, FacetCodecRegistry().register(AndroidFacetCodec))
            ModuleTypeRegistry(platform.extensions).register(AndroidAppModuleType, AndroidSupport.PLUGIN)
            val appType = ModuleTypeRegistry(platform.extensions).resolve("android-app")
            store.workspace.beginModification().apply { addProject("demo", BuildSystemId.NATIVE, store.vfs.root()); commit() }
            store.workspace.libraryTable.create("jarlib").apply { kind = LibraryKind.JAR; addClassesRoot(store.vfs.fileFor(jarLib)); commit() }
            store.workspace.projects.single().beginModification().apply {
                addModule("app", appType).apply {
                    languageLevel = LanguageLevel.JAVA_17
                    putFacet(AndroidFacet(namespace = "com.example.app", compileSdk = 34, minSdk = 24, targetSdk = 34))
                    addDependency(LibraryDependency(LibraryRef("jarlib"), DependencyScope.IMPLEMENTATION))
                }
                commit()
            }
            dir.writeSource("app/src/main/AndroidManifest.xml", MANIFEST)
            dir.writeSource("app/src/main/res/values/strings.xml", STRINGS)
            dir.writeSource("app/src/main/java/com/example/app/MainActivity.java", activity("one"))

            val signing = DebugKeystore.getOrCreate(dir.resolve(".keystore/debug.ks"), sdk.keytool)
            // Force the off-heap one-pass external-dex path (dexExtLibs) this test exercises. On device that path
            // is a forked big-heap VM, gated by AndroidBuildSystem on Dexer.runsOffHeap(); an in-process D8 reports
            // false (it would thrash a whole-classpath pass on a small heap → bounded per-lib archiving instead),
            // so wrap it to report off-heap here. The dex work still runs in-process; only the task graph is selected.
            val buildSystem = AndroidBuildSystem.inProcess(sdk, signing, mergeDexer = OffHeapDexer())
            val request = BuildRequest(listOf(ModuleId("app")), VariantSelector("debug"), BuildGoal.PACKAGE)
            val cache = BuildCache(dir.resolve(".caches/build"))
            fun build() = runBlocking {
                TaskExecutorImpl(cache).execute(buildSystem.createBuildGraph(store.workspace.projects.single(), request), SimpleTaskContext(), 2)
            }

            // dexBuilder archives the app; the external library is dexed by dexExtLibs (one forked pass to indexed
            // dex — minSdk 24 desugars, so per-lib buckets buy no incrementality); mergeProjectDex combines the app.
            val first = build()
            assertTrue(first.succeeded, "first build failed")
            val firstRan = first.ranTasks.map { it.value }
            assertTrue(":app:dexBuilderDebug" in firstRan, "dexBuilder should run first time; ran=$firstRan")
            assertTrue(":app:mergeProjectDexDebug" in firstRan, "project dex should merge first time; ran=$firstRan")
            assertTrue(":app:dexExtLibsDebug" in firstRan, "external-library dex should run first time; ran=$firstRan")

            // Edit only the app source, then rebuild.
            dir.writeSource("app/src/main/java/com/example/app/MainActivity.java", activity("two"))
            val second = build()
            assertTrue(second.succeeded, "incremental build failed")
            val ran = second.ranTasks.map { it.value }
            val skipped = second.skippedTasks.map { it.value }
            // dexBuilder re-runs (as in AGP) but only re-archives the changed app; the external library's inputs
            // are untouched, so dexExtLibs skips (its indexed output is reused) — proof the library is NOT re-dexed.
            assertTrue(":app:dexBuilderDebug" in ran, "edited app must re-run dexBuilder; ran=$ran")
            assertTrue(":app:mergeProjectDexDebug" in ran, "project-dex merge must re-run; ran=$ran")
            assertTrue(":app:dexExtLibsDebug" in skipped, "unchanged external-library dex must skip; skipped=$skipped")
        }
    }

    private fun compileJar(workDir: Path, androidJar: Path): Path {
        val src = workDir.resolve("src/com/example/jarlib/JarGreeter.java")
        Files.createDirectories(src.parent)
        Files.writeString(src, "package com.example.jarlib; public final class JarGreeter { public static String hello() { return \"jar\"; } }")
        val classes = workDir.resolve("classes")
        check(JdtBatchCompiler.compile(listOf(src), listOf(androidJar), classes, "17").success) { "lib compile failed" }
        val jar = workDir.resolve("jarlib.jar")
        ApkPackaging.jarClasses(classes, jar)
        return jar
    }

    private fun activity(tag: String) = """
        package com.example.app;

        import android.app.Activity;
        import android.os.Bundle;
        import android.widget.TextView;
        import com.example.jarlib.JarGreeter;

        public class MainActivity extends Activity {
            @Override
            protected void onCreate(Bundle savedInstanceState) {
                super.onCreate(savedInstanceState);
                TextView tv = new TextView(this);
                tv.setText(JarGreeter.hello() + "$tag");
                setContentView(tv);
            }
        }
    """

    private companion object {
        val MANIFEST = """
            <?xml version="1.0" encoding="utf-8"?>
            <manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.example.app">
                <application android:label="@string/app_name"><activity android:name=".MainActivity" android:exported="true"/></application>
            </manifest>
        """
        val STRINGS = """
            <?xml version="1.0" encoding="utf-8"?>
            <resources><string name="app_name">Inc</string></resources>
        """
    }

    /** In-process D8 that reports itself as off-heap, so the build selects the one-pass external-dex path
     *  (dexExtLibs). Dexing still runs in-process; only [Dexer.runsOffHeap] (the path gate) is overridden. */
    private class OffHeapDexer(private val delegate: Dexer = D8InProcessDexer()) : Dexer by delegate {
        override fun runsOffHeap(): Boolean = true
    }
}
