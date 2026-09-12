package no.f12.codenavigator.gradle

import no.f12.codenavigator.config.OutputFormat
import no.f12.codenavigator.formatting.OutputWrapper
import no.f12.codenavigator.navigation.ambient.AmbientFormatter
import no.f12.codenavigator.navigation.ambient.AmbientGuidance
import no.f12.codenavigator.navigation.ambient.AmbientOrchestrator
import no.f12.codenavigator.navigation.ambient.AmbientTaskConfig
import no.f12.codenavigator.registry.BuildTool
import no.f12.codenavigator.registry.TaskRegistry
import org.gradle.api.GradleException
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import org.gradle.work.DisableCachingByDefault
import java.io.File

@DisableCachingByDefault(because = "Produces console output only")
abstract class AmbientTask : CodeNavigatorTask() {

    @Option(option = "domain", description = "Regex naming the domain classes to check (default: ring 0 as cnavRings computes it)")
    @get:Internal
    var domain: String? = null

    @Option(option = "categories", description = "Which kinds of ambient input to check for: clock,random,env,io (default: all)")
    @get:Internal
    var categories: String? = null

    @Option(option = "detail", description = "Show individual call details")
    @get:Internal
    var detail: String? = null

    @Option(option = "exclude", description = "Exclude results matching this regex")
    @get:Internal
    var exclude: String? = null

    @Option(option = "scope", description = "Filter by source set: all (default), prod (production only), test (test only)")
    @get:Internal
    var scope: String? = null

    @Option(option = "fail-on-violation", description = "Fail the build when findings exceed the configured threshold")
    @get:Internal
    var failOnViolation: String? = null

    @Option(option = "max-violations", description = "Max allowed findings before failing the build (used with --fail-on-violation)")
    @get:Internal
    var maxViolations: String? = null

    override fun taskOptionsMap(): Map<String, String?> = buildMap {
        domain?.let { put("domain", it) }
        categories?.let { put("categories", it) }
        detail?.let { put("detail", it) }
        exclude?.let { put("exclude", it) }
        scope?.let { put("scope", it) }
        failOnViolation?.let { put("fail-on-violation", it) }
        maxViolations?.let { put("max-violations", it) }
    }

    @TaskAction
    fun showAmbient() {
        val properties = TaskRegistry.AMBIENT.enhanceProperties(buildOptionsMap())
        val config = try {
            AmbientTaskConfig.parse(properties)
        } catch (e: IllegalArgumentException) {
            throw GradleException("${e.message}\n${TaskRegistry.AMBIENT.usageHint(BuildTool.GRADLE)}")
        }

        val taggedDirs = project.taggedClassDirectories()
        val cacheDir = File(project.layout.buildDirectory.asFile.get(), "cnav")
        val reportFile = File(cacheDir, "skipped-files.txt")

        val output = AmbientOrchestrator.run(config, taggedDirs, reportFile, project.projectDir)

        output.skippedFileWarning?.let { logger.warn(it) }

        if (output.subjectsUnavailable) {
            logger.quiet(OutputWrapper.wrapWithGuidance(
                "cnavRings could not derive a layered hexagon for this project, so there is no ring 0 to check. " +
                    "Pass --domain=<regex> to name the domain classes directly.",
                config.format,
                AmbientGuidance.GUIDANCE,
            ))
            return
        }

        val result = output.result
        if (result == null || result.all.isEmpty()) {
            logger.quiet(OutputWrapper.wrapWithGuidance(AmbientFormatter.NO_FINDINGS, config.format, AmbientGuidance.GUIDANCE))
            return
        }

        logger.quiet(OutputWrapper.wrapWithGuidance(
            when (config.format) {
                OutputFormat.LLM -> AmbientFormatter.formatLlm(result)
                OutputFormat.JSON -> AmbientFormatter.formatJson(result)
                else -> AmbientFormatter.formatText(result, config.detail)
            },
            config.format,
            AmbientGuidance.GUIDANCE,
        ))

        if (config.failOnViolation && result.actionable.size > config.maxViolations) {
            throw GradleException(
                "Ambient input check failed: ${result.actionable.size} finding(s) exceed the allowed maximum of ${config.maxViolations}. " +
                    "See output above for details.",
            )
        }
    }
}
