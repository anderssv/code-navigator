package no.f12.codenavigator.gradle

import no.f12.codenavigator.config.OutputFormat
import no.f12.codenavigator.formatting.OutputWrapper
import no.f12.codenavigator.registry.BuildTool
import no.f12.codenavigator.registry.TaskRegistry
import no.f12.codenavigator.navigation.testcoupling.TestCouplingFormatter
import no.f12.codenavigator.navigation.testcoupling.TestCouplingGuidance
import no.f12.codenavigator.navigation.testcoupling.TestCouplingOrchestrator
import no.f12.codenavigator.navigation.testcoupling.TestCouplingTaskConfig

import org.gradle.api.GradleException
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import org.gradle.work.DisableCachingByDefault
import java.io.File

@DisableCachingByDefault(because = "Produces console output only")
abstract class TestCouplingTask : CodeNavigatorTask() {

    @Option(option = "ports", description = "Regex matching port interface names (hexagonal boundaries that get faked in tests, e.g. .*Repository|.*Client)")
    @get:Internal
    var ports: String? = null

    @Option(option = "detail", description = "Show individual call details")
    @get:Internal
    var detail: String? = null

    @Option(option = "exclude", description = "Exclude results matching this regex")
    @get:Internal
    var exclude: String? = null

    @Option(option = "scope", description = "Filter by source set: all (default), prod (production only), test (test only)")
    @get:Internal
    var scope: String? = null

    @Option(option = "subject", description = "Who's checked for calling a port directly: tests (default), adapters, or both")
    @get:Internal
    var subject: String? = null

    @Option(option = "write-methods", description = "Force these port methods (Iface.method, comma-separated) to be treated as writes for --subject=adapters")
    @get:Internal
    var writeMethods: String? = null

    @Option(option = "read-methods", description = "Force these port methods (Iface.method, comma-separated) to be treated as reads for --subject=adapters")
    @get:Internal
    var readMethods: String? = null

    @Option(option = "fail-on-violation", description = "Fail the build when violations exceed the configured threshold")
    @get:Internal
    var failOnViolation: String? = null

    @Option(option = "max-violations", description = "Max allowed violations before failing the build (used with --fail-on-violation)")
    @get:Internal
    var maxViolations: String? = null

    override fun taskOptionsMap(): Map<String, String?> = buildMap {
        ports?.let { put("ports", it) }
        detail?.let { put("detail", it) }
        exclude?.let { put("exclude", it) }
        scope?.let { put("scope", it) }
        subject?.let { put("subject", it) }
        writeMethods?.let { put("write-methods", it) }
        readMethods?.let { put("read-methods", it) }
        failOnViolation?.let { put("fail-on-violation", it) }
        maxViolations?.let { put("max-violations", it) }
    }

    @TaskAction
    fun showTestCoupling() {
        val properties = TaskRegistry.TEST_COUPLING.enhanceProperties(buildOptionsMap())
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

        val output = TestCouplingOrchestrator.run(config, taggedDirs, cacheDir, reportFile, project.projectDir)

        output.skippedFileWarning?.let { logger.warn(it) }

        if (output.noPortsFound) {
            val guidance = TestCouplingGuidance.GUIDANCE
            logger.quiet(OutputWrapper.wrapWithGuidance(
                "No interfaces matching '${config.ports.pattern}' found. Adjust --ports to match your port interface names.",
                config.format,
                guidance,
            ))
            return
        }

        val result = output.result
        if (result == null || result.violations.isEmpty()) {
            logger.quiet(OutputWrapper.wrapWithGuidance(
                "No TTTD violations found. All test classes use domain-oriented setup.",
                config.format,
                TestCouplingGuidance.GUIDANCE,
            ))
            return
        }

        val guidance = TestCouplingGuidance.GUIDANCE
        logger.quiet(OutputWrapper.wrapWithGuidance(
            when {
                config.format == OutputFormat.LLM -> TestCouplingFormatter.formatLlm(result)
                config.detail -> TestCouplingFormatter.formatDetailText(result)
                else -> TestCouplingFormatter.formatText(result)
            },
            config.format,
            guidance,
        ))

        if (config.failOnViolation && result.actionableViolations.size > config.maxViolations) {
            throw GradleException(
                "Test coupling check failed: ${result.actionableViolations.size} violation(s) exceed the allowed maximum of ${config.maxViolations}. " +
                    "See output above for details.",
            )
        }
    }
}
