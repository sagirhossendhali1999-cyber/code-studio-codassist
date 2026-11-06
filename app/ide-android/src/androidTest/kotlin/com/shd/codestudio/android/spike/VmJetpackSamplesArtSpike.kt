package com.shd.codestudio.android.spike

import android.util.Log
import androidx.activity.compose.setContent
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.currentComposer
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.shd.codestudio.android.DexPeerFactory
import com.shd.codestudio.interp.compose.ComposeDispatcher
import com.shd.codestudio.interp.compose.VmLibraryExecutor
import com.shd.codestudio.jvm.ClassBytesSource
import com.shd.codestudio.lang.kotlin.interp.CallSiteKey
import com.shd.codestudio.lang.kotlin.interp.DispatchKind
import com.shd.codestudio.lang.kotlin.interp.RNode
import com.shd.codestudio.lang.kotlin.interp.ResolvedCallable
import com.shd.codestudio.lang.kotlin.interp.SourceSpan
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.ZipInputStream
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Device conformance sweep for the Material3 interpret flip: interpret a curated corpus of REAL Jetpack Compose
 * Material3 samples (the [com.shd.codestudio.interp.compose.jetpacksamples] fixture, faithful to the AndroidX
 * `androidx.compose.material3.samples` bodies) on ART, each composed into a real Compose UI. Material3 is
 * interpreted from the staged `material3-android.jar` (the flip); the sample facade + its `ComposableSingletons`
 * content-lambda holder are interpreted from staged `.class`; foundation/ui/runtime bridge to the app's dexed
 * Compose. Reports per-sample pass/fail so VM gaps surface at scale (each failure carries `… [in <method>@<pc>]`).
 *
 *     JAVA_HOME=<Android Studio JBR> ./gradlew :ide-android:connectedDebugAndroidTest \
 *       -Pandroid.testInstrumentationRunnerArguments.class=com.shd.codestudio.android.spike.VmJetpackSamplesArtSpike
 *     adb logcat -d -s VmSamplesArt
 */
@RunWith(AndroidJUnit4::class)
class VmJetpackSamplesArtSpike {

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val samplesFacade = "com.shd.codestudio.interp.compose.jetpacksamples.JetpackSamplesKt"

    private val samples = listOf(
        "ButtonSample", "ElevatedButtonSample", "FilledTonalButtonSample", "OutlinedButtonSample", "TextButtonSample",
        "FloatingActionButtonSample", "ExtendedFabSample", "IconButtonSample", "FilledIconButtonSample", "OutlinedIconButtonSample",
        "CheckboxSample", "SwitchSample", "RadioButtonSample", "SliderSample",
        "AssistChipSample", "FilterChipSample", "SuggestionChipSample",
        "CardSample", "SurfaceSample", "ScaffoldSample", "TopAppBarSample", "ListItemSample", "BadgeSample", "SnackbarSample", "HorizontalDividerSample",
        "TextFieldSample", "OutlinedTextFieldSample",
        "NavigationBarSample", "TabRowSample",
        "LinearProgressIndicatorSample", "CircularProgressIndicatorSample", "LinearIndeterminateSample", "CircularIndeterminateSample",
        "TextSample", "MiniScreenSample",
    )

    private fun jarBytes(asset: String): Map<String, ByteArray> {
        val out = HashMap<String, ByteArray>()
        instrumentation.context.assets.open(asset).use { raw ->
            ZipInputStream(raw).use { zip ->
                while (true) {
                    val e = zip.nextEntry ?: break
                    if (e.name.endsWith(".class")) out[e.name.removeSuffix(".class")] = zip.readBytes()
                    zip.closeEntry()
                }
            }
        }
        return out
    }

    private fun assetBytes(asset: String): ByteArray =
        instrumentation.context.assets.open(asset).use { it.readBytes() }

    private fun log(m: String) { Log.i("VmSamplesArt", m); println(m) }

    @Test
    fun interpretsJetpackMaterial3SamplesOnArt() {
        val m3 = jarBytes("vmbench/material3-android.jar")
        val fixture = mapOf(
            "com/shd/codestudio/interp/compose/jetpacksamples/JetpackSamplesKt" to assetBytes("vmbench/JetpackSamplesKt.class"),
            "com/shd/codestudio/interp/compose/jetpacksamples/ComposableSingletons\$JetpackSamplesKt" to assetBytes("vmbench/ComposableSingletons\$JetpackSamplesKt.class"),
        )
        log("staged material3 classes: ${m3.size}, fixture classes: ${fixture.size}")
        val executor = VmLibraryExecutor(
            source = ClassBytesSource { name -> fixture[name] ?: m3[name] },
            projectPreferredPrefixes = listOf("androidx.compose.material3."),
            peerFactory = DexPeerFactory(),
        )
        val d = ComposeDispatcher(libraryExecutor = executor)
        val span = SourceSpan(0, 0)
        fun callFor(name: String) = RNode.Call(
            callee = ResolvedCallable.Library(
                displayName = name, ownerFqn = samplesFacade, methodName = name,
                paramTypes = emptyList(), isStatic = true, isConstructor = false, isInline = false, isComposable = true,
            ),
            dispatch = DispatchKind.TOP_LEVEL, receiver = null, args = emptyList(),
            callSiteKey = CallSiteKey(name.hashCode()), source = span,
        )

        val results = LinkedHashMap<String, String>()
        // A FRESH activity per sample: each is an initial composition (re-calling setContent on one activity
        // doesn't reliably re-run + fire SideEffect). The executor/VM (1374 parsed classes) is reused.
        for (name in samples) {
            val composed = AtomicBoolean(false)
            val err = arrayOfNulls<Throwable>(1)
            ActivityScenario.launch(SpikeComposeActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    activity.setContent {
                        d.composer = currentComposer
                        val ok = try {
                            d.dispatch(callFor(name), receiver = null, args = emptyList())
                            true
                        } catch (t: Throwable) {
                            err[0] = t; false
                        }
                        if (ok) SideEffect { composed.set(true) }
                    }
                }
                instrumentation.waitForIdleSync()
            }
            val root = err[0]?.let { generateSequence(it) { t -> t.cause }.last() }
            results[name] = when {
                composed.get() && err[0] == null -> "OK"
                // A missing NEWER platform class (e.g. TextField references android.app.PictureInPictureUiState,
                // API 31) on an older emulator is a platform/API-level limit, not a VM gap — the bridged/reflective
                // path hits it identically. Classify as a skip so the sweep stays a real-VM-gap guard.
                root is ClassNotFoundException && root.message?.contains("android.") == true ->
                    "SKIP(platform-api): ${root.message?.substringBefore(" on path")}"
                else -> "FAIL: ${root?.let { "${it::class.java.simpleName}: ${it.message}" } ?: "did not compose"}"
            }
            log("  $name -> ${results[name]}") // logged per-sample so a mid-sweep crash still leaves a trail
        }
        val passed = results.values.count { it == "OK" }
        val skipped = results.values.count { it.startsWith("SKIP") }
        val failed = results.size - passed - skipped
        log("JETPACK MATERIAL3 SAMPLES: $passed OK, $skipped skipped(platform-api), $failed failed of ${results.size} on ART")
        results.forEach { (n, r) -> log("  $n -> $r") }
        // Green unless a sample fails for a REAL reason (a VM/interpreter gap) — platform-API skips are tolerated.
        assertTrue("Jetpack Material3 samples must interpret on ART (platform-api skips OK) — see VmSamplesArt logcat: $results", failed == 0)
    }
}
