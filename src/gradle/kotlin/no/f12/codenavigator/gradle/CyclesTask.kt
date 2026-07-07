package no.f12.codenavigator.gradle

import no.f12.codenavigator.config.OutputFormat

import no.f12.codenavigator.formatting.JsonFormatter
import no.f12.codenavigator.formatting.LlmFormatter
import no.f12.codenavigator.formatting.OutputWrapper
import no.f12.codenavigator.registry.TaskRegistry
import no.f12.codenavigator.navigation.dsm.CyclesConfig
import no.f12.codenavigator.navigation.dsm.CyclesFormatter
import no.f12.codenavigator.navigation.dsm.CyclesOrchestrator
import org.gradle.api.GradleException
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import org.gradle.work.DisableCachingByDefault
import java.io.File

@DisableCachingByDefault(because = "Produces console output only")
abstract class CyclesTask : CodeNavigatorTask() {

    @Option(option = "package-filter", description = "Only include packages under this prefix")
    @get:Internal
    var packageFilter: String? = null

    @Option(option = "include-external", description = "Include dependencies on classes outside the project")
    @get:Internal
    var includeExternal: String? = null

    @Option(option = "dsm-depth", description = "Package grouping depth")
    @get:Internal
    var dsmDepth: String? = null

    @Option(option = "root-package", description = "Deprecated: use package-filter instead. Only include packages under this prefix")
    @get:Internal
    var rootPackage: String? = null

    @Option(option = "scope", description = "Filter by source set: all (default), prod (production only), test (test only)")
    @get:Internal
    var scope: String? = null

    @Option(option = "fail-on-violation", description = "Fail the build when cycle count exceeds --max-cycles")
    @get:Internal
    var failOnViolation: String? = null

    @Option(option = "max-cycles", description = "Max allowed cycles before failing the build (used with --fail-on-violation)")
    @get:Internal
    var maxCycles: String? = null

    override fun taskOptionsMap(): Map<String, String?> = buildMap {
        packageFilter?.let { put("package-filter", it) }
        includeExternal?.let { put("include-external", it) }
        dsmDepth?.let { put("dsm-depth", it) }
        rootPackage?.let { put("root-package", it) }
        scope?.let { put("scope", it) }
        failOnViolation?.let { put("fail-on-violation", it) }
        maxCycles?.let { put("max-cycles", it) }
    }

    @TaskAction
    fun showCycles() {
        val extension = project.codeNavigatorExtension()
        val cliProps = TaskRegistry.CYCLE_DETECTION.enhanceProperties(buildOptionsMap())
        val props = extension.resolveProperties(cliProps)

        val config = CyclesConfig.parse(props)
        config.deprecations().forEach { logger.warn(it) }

        val taggedDirs = project.taggedClassDirectories()
        val reportFile = File(project.layout.buildDirectory.asFile.get(), "cnav/skipped-files.txt")
        val output = CyclesOrchestrator.run(config, taggedDirs, loadPlanSteps(), reportFile)

        output.skippedFileWarning?.let { logger.warn(it) }

        if (output.details.isEmpty()) {
            logger.lifecycle(OutputWrapper.emptyResult(config.format, "No package cycles detected."))
            return
        }

        logger.lifecycle(OutputWrapper.formatAndWrap(config.format) { format ->
    when (format) {
        OutputFormat.TEXT, OutputFormat.DIFF -> CyclesFormatter.format(output.details, displayPrefix = output.displayPrefix, testInvolvement = output.testInvolvement)
        OutputFormat.JSON -> JsonFormatter.formatCycles(output.details, displayPrefix = output.displayPrefix, testInvolvement = output.testInvolvement)
        OutputFormat.LLM -> LlmFormatter.formatCycles(output.details, displayPrefix = output.displayPrefix, testInvolvement = output.testInvolvement)
    }
})

        if (config.failOnViolation && output.details.size > config.maxCycles) {
            throw GradleException("cnavCycles found ${output.details.size} cycle(s), exceeding --max-cycles=${config.maxCycles}")
        }
    }
}
