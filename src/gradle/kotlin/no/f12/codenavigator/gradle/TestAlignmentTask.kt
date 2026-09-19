package no.f12.codenavigator.gradle

import no.f12.codenavigator.config.OutputFormat
import no.f12.codenavigator.formatting.OutputWrapper
import no.f12.codenavigator.navigation.testalignment.TestAlignmentFormatter
import no.f12.codenavigator.navigation.testalignment.TestAlignmentGuidance
import no.f12.codenavigator.navigation.testalignment.TestAlignmentOrchestrator
import no.f12.codenavigator.navigation.testalignment.TestAlignmentTaskConfig
import no.f12.codenavigator.registry.TaskRegistry
import org.gradle.api.GradleException
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import org.gradle.work.DisableCachingByDefault
import java.io.File

@DisableCachingByDefault(because = "Produces console output only")
abstract class TestAlignmentTask : CodeNavigatorTask() {

    @Option(option = "exclude", description = "Exclude test classes matching this regex")
    @get:Internal
    var exclude: String? = null

    @Option(option = "fail-on-violation", description = "Fail the build when findings exceed the configured threshold (--no-fail-on-violation switches it back off)")
    @get:Internal
    var failOnViolation: Boolean? = null

    @Option(option = "max-violations", description = "Max allowed findings before failing the build (used with --fail-on-violation)")
    @get:Internal
    var maxViolations: String? = null

    override fun taskOptionsMap(): Map<String, String?> = buildMap {
        exclude?.let { put("exclude", it) }
        failOnViolation?.let { put("fail-on-violation", it.toString()) }
        maxViolations?.let { put("max-violations", it) }
    }

    @TaskAction
    fun showTestAlignment() {
        val config = TestAlignmentTaskConfig.parse(
            TaskRegistry.TEST_ALIGNMENT.enhanceProperties(buildOptionsMap()),
        )

        val cacheDir = File(project.layout.buildDirectory.asFile.get(), "cnav")
        val output = TestAlignmentOrchestrator.run(
            config,
            project.taggedClassDirectories(),
            cacheDir,
            File(cacheDir, "skipped-files.txt"),
        )
        output.skippedFileWarning?.let { logger.warn(it) }

        if (output.noTestClasses) {
            logger.quiet(
                OutputWrapper.wrapWithGuidance(
                    "No compiled test classes found — run the test compilation first.",
                    config.format,
                    TestAlignmentGuidance.GUIDANCE,
                ),
            )
            return
        }

        val result = output.result ?: return

        logger.quiet(
            OutputWrapper.wrapWithGuidance(
                when (config.format) {
                    OutputFormat.JSON -> TestAlignmentFormatter.formatJson(result)
                    OutputFormat.LLM -> TestAlignmentFormatter.formatLlm(result)
                    else -> TestAlignmentFormatter.formatText(result)
                },
                config.format,
                TestAlignmentGuidance.GUIDANCE,
            ),
        )

        if (config.failOnViolation && result.violationCount > config.maxViolations) {
            throw GradleException(
                "Test alignment check failed: ${result.violationCount} violation(s) exceed the allowed maximum of ${config.maxViolations}. " +
                    "See output above for details.",
            )
        }
    }
}
