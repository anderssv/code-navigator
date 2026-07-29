package no.f12.codenavigator.gradle

import no.f12.codenavigator.config.OutputFormat
import no.f12.codenavigator.formatting.OutputWrapper
import no.f12.codenavigator.navigation.classmetrics.ClassMetricsConfig
import no.f12.codenavigator.navigation.classmetrics.ClassMetricsFormatter
import no.f12.codenavigator.navigation.classmetrics.ClassMetricsOrchestrator
import no.f12.codenavigator.registry.TaskRegistry
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import org.gradle.work.DisableCachingByDefault
import java.io.File

@DisableCachingByDefault(because = "Produces console output only")
abstract class ClassMetricsTask : CodeNavigatorTask() {

    @Option(option = "min-methods", description = "Minimum eligible method count to include a class")
    @get:Internal
    var minMethods: String? = null

    @Option(option = "min-tcc", description = "Minimum Tight Class Cohesion to include a class")
    @get:Internal
    var minTcc: String? = null

    @Option(option = "max-wmc", description = "Maximum WMC (summed cyclomatic complexity) to include a class")
    @get:Internal
    var maxWmc: String? = null

    @Option(option = "max-cbo", description = "Maximum CBO (coupling between objects) to include a class")
    @get:Internal
    var maxCbo: String? = null

    @Option(option = "scope", description = "Filter by source set: all (default), prod (production only), test (test only)")
    @get:Internal
    var scope: String? = null

    override fun taskOptionsMap(): Map<String, String?> = buildMap {
        minMethods?.let { put("min-methods", it) }
        minTcc?.let { put("min-tcc", it) }
        maxWmc?.let { put("max-wmc", it) }
        maxCbo?.let { put("max-cbo", it) }
        scope?.let { put("scope", it) }
    }

    @TaskAction
    fun showClassMetrics() {
        val extension = project.codeNavigatorExtension()
        val props = extension.resolveProperties(TaskRegistry.CLASS_METRICS.enhanceProperties(buildOptionsMap()))
        val config = ClassMetricsConfig.parse(props)

        val taggedDirs = project.taggedClassDirectories()
        val classDirectories = taggedDirs.filter { config.scope.matchesSourceSet(it.second) }.map { it.first }

        val reportFile = File(project.layout.buildDirectory.asFile.get(), "cnav/skipped-files.txt")
        val output = ClassMetricsOrchestrator.run(config, classDirectories, reportFile)

        output.skippedFileWarning?.let { logger.warn(it) }

        if (output.results.isEmpty()) {
            logger.quiet(OutputWrapper.emptyResult(config.format, "No matching classes found."))
            return
        }

        logger.quiet(OutputWrapper.formatAndWrap(config.format) { format ->
    when (format) {
        OutputFormat.TEXT, OutputFormat.DIFF -> ClassMetricsFormatter.format(output.results)
        OutputFormat.JSON -> ClassMetricsFormatter.formatJson(output.results)
        OutputFormat.LLM -> ClassMetricsFormatter.formatLlm(output.results)
    }
})
    }
}
