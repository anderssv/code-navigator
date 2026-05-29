package no.f12.codenavigator.gradle

import no.f12.codenavigator.config.OutputFormat
import no.f12.codenavigator.formatting.OutputWrapper
import no.f12.codenavigator.registry.BuildTool
import no.f12.codenavigator.registry.TaskRegistry
import no.f12.codenavigator.navigation.testcoupling.TestCouplingFormatter
import no.f12.codenavigator.navigation.testcoupling.TestCouplingGuidance
import no.f12.codenavigator.navigation.testcoupling.TestCouplingOrchestrator
import no.f12.codenavigator.navigation.testcoupling.TestCouplingTaskConfig

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.File

@DisableCachingByDefault(because = "Produces console output only")
abstract class TestCouplingTask : DefaultTask() {

    @TaskAction
    fun showTestCoupling() {
        val properties = project.buildPropertyMap(TaskRegistry.TEST_COUPLING)
        val config = try {
            TestCouplingTaskConfig.parse(properties)
        } catch (e: IllegalArgumentException) {
            throw GradleException(
                "${e.message}\n${TaskRegistry.TEST_COUPLING.usageHint(BuildTool.GRADLE)}",
            )
        }

        val taggedDirs = project.taggedClassDirectories()
        val cacheDir = File(project.layout.buildDirectory.asFile.get(), "cnav")
        val reportFile = File(cacheDir, "skipped-files.txt")

        val output = TestCouplingOrchestrator.run(config, taggedDirs, cacheDir, reportFile)

        output.skippedFileWarning?.let { logger.warn(it) }

        if (output.noPortsFound) {
            val guidance = TestCouplingGuidance.GUIDANCE
            logger.lifecycle(OutputWrapper.wrapWithGuidance(
                "No interfaces matching '${config.ports.pattern}' found. Adjust -Pports to match your port interface names.",
                config.format,
                guidance,
            ))
            return
        }

        val result = output.result
        if (result == null || result.violations.isEmpty()) {
            logger.lifecycle(OutputWrapper.wrapWithGuidance(
                "No TTTD violations found. All test classes use domain-oriented setup.",
                config.format,
                TestCouplingGuidance.GUIDANCE,
            ))
            return
        }

        val guidance = TestCouplingGuidance.GUIDANCE
        logger.lifecycle(OutputWrapper.wrapWithGuidance(
            when (config.format) {
                OutputFormat.TEXT -> TestCouplingFormatter.formatText(result)
                OutputFormat.JSON -> TestCouplingFormatter.formatText(result) // TODO: add JSON formatter
                OutputFormat.LLM -> TestCouplingFormatter.formatLlm(result)
            },
            config.format,
            guidance,
        ))
    }
}
