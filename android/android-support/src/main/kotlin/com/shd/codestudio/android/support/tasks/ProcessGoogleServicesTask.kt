package com.shd.codestudio.android.support.tasks

import com.shd.codestudio.android.support.gms.GoogleServices
import com.shd.codestudio.build.BuildDiagnostic
import com.shd.codestudio.build.BuildSeverity
import com.shd.codestudio.build.DiagnosticKind
import com.shd.codestudio.build.Task
import com.shd.codestudio.build.TaskContext
import com.shd.codestudio.build.TaskInputs
import com.shd.codestudio.build.TaskInputsImpl
import com.shd.codestudio.build.TaskName
import com.shd.codestudio.build.TaskOutputs
import com.shd.codestudio.build.TaskOutputsImpl
import com.shd.codestudio.build.TaskResult
import com.shd.codestudio.build.engine.debug
import com.shd.codestudio.build.engine.report
import java.nio.file.Files
import java.nio.file.Path

/**
 * `process<Variant>GoogleServices`: parse the module's `google-services.json` and write the generated
 * `values.xml` (`google_app_id`, `gcm_defaultSenderId`, `project_id`, …) into [outResDir], which the
 * resource merge then picks up. The on-device counterpart of the Google Services Gradle plugin. Runs only
 * when a `google-services.json` is present; a present-but-mismatched file fails the build (as the plugin does).
 */
internal class ProcessGoogleServicesTask(
    override val name: TaskName,
    private val googleServicesJson: Path,
    private val applicationId: String,
    private val fallbackPackage: String,
    private val outResDir: Path,
) : Task {
    override val inputs: TaskInputs
        get() = TaskInputsImpl().apply {
            filePaths("json", listOf(googleServicesJson).filter { Files.exists(it) })
            property("applicationId", applicationId)
            property("fallbackPackage", fallbackPackage)
        }
    override val outputs: TaskOutputs get() = TaskOutputsImpl().apply { dirPath("res", outResDir) }

    override suspend fun execute(ctx: TaskContext): TaskResult {
        ctx.checkCanceled()
        val text = runCatching { Files.readAllBytes(googleServicesJson).toString(Charsets.UTF_8) }
            .getOrElse { return TaskResult.Failed("cannot read ${googleServicesJson.fileName}: ${it.message}") }

        val valuesFile = outResDir.resolve("values").resolve("values.xml")
        return when (val outcome = GoogleServices.process(text, applicationId, fallbackPackage)) {
            is GoogleServices.Outcome.Failure -> {
                // Our own check, so the problem is stated directly rather than printed and parsed back.
                ctx.report(
                    BuildDiagnostic(
                        BuildSeverity.ERROR, outcome.message, DiagnosticKind.RESOURCE, source = "google-services",
                    )
                )
                TaskResult.Failed(outcome.message)
            }
            is GoogleServices.Outcome.Success -> {
                outcome.messages.forEach { ctx.debug(it) }
                Files.createDirectories(valuesFile.parent)
                Files.write(valuesFile, GoogleServices.valuesXml(outcome.resources).toByteArray(Charsets.UTF_8))
                ctx.debug("processGoogleServices -> ${outcome.resources.size} resource(s) for ${outcome.matchedPackage}")
                TaskResult.Success
            }
        }
    }
}
