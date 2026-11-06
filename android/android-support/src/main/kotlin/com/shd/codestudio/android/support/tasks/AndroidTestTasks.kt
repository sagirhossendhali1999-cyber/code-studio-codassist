package com.shd.codestudio.android.support.tasks

import com.shd.codestudio.build.Task
import com.shd.codestudio.build.TaskContext
import com.shd.codestudio.build.TaskInputs
import com.shd.codestudio.build.TaskInputsImpl
import com.shd.codestudio.build.TaskName
import com.shd.codestudio.build.TaskOutputs
import com.shd.codestudio.build.TaskOutputsImpl
import com.shd.codestudio.build.TaskResult
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText

/**
 * The manifest of an instrumented test APK, as AGP generates it (`processDebugAndroidTestManifest`): its own package
 * ([testPackage], the app's id + `.test`), an `<instrumentation>` naming the [runner] and the app it runs inside
 * ([targetPackage]), and the `android.test.runner` shared library the old runners load from. The test libraries'
 * own manifests (androidx.test's activities, for one) are merged into it afterwards like an app's.
 */
internal class InstrumentationManifestTask(
    override val name: TaskName,
    private val testPackage: String,
    private val targetPackage: String,
    private val runner: String,
    private val minSdk: Int,
    private val targetSdk: Int,
    private val out: Path,
) : Task {
    override val inputs: TaskInputs
        get() = TaskInputsImpl().apply {
            property("testPackage", testPackage)
            property("targetPackage", targetPackage)
            property("runner", runner)
            property("minSdk", minSdk)
            property("targetSdk", targetSdk)
        }
    override val outputs: TaskOutputs get() = TaskOutputsImpl().apply { filePath("manifest", out) }

    override suspend fun execute(ctx: TaskContext): TaskResult {
        ctx.checkCanceled()
        Files.createDirectories(out.parent)
        out.writeText(manifest(testPackage, targetPackage, runner, minSdk, targetSdk))
        return TaskResult.Success
    }

    companion object {
        fun manifest(testPackage: String, targetPackage: String, runner: String, minSdk: Int, targetSdk: Int): String = """
            |<?xml version="1.0" encoding="utf-8"?>
            |<manifest xmlns:android="http://schemas.android.com/apk/res/android"
            |    package="$testPackage">
            |    <uses-sdk android:minSdkVersion="$minSdk" android:targetSdkVersion="$targetSdk" />
            |    <application android:debuggable="true">
            |        <uses-library android:name="android.test.runner" android:required="false" />
            |    </application>
            |    <instrumentation
            |        android:name="$runner"
            |        android:targetPackage="$targetPackage"
            |        android:handleProfiling="false"
            |        android:functionalTest="false"
            |        android:label="Tests for $targetPackage" />
            |</manifest>
            |""".trimMargin()
    }
}
