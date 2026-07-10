package no.f12.codenavigator.gradle

import no.f12.codenavigator.config.OutputFormat

import no.f12.codenavigator.formatting.OutputWrapper
import no.f12.codenavigator.registry.TaskRegistry
import no.f12.codenavigator.navigation.deadcode.DeadCodeConfig
import no.f12.codenavigator.navigation.metrics.MetricsConfig
import no.f12.codenavigator.navigation.metrics.MetricsFormatter
import no.f12.codenavigator.navigation.metrics.MetricsOrchestrator

import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import org.gradle.work.DisableCachingByDefault
import java.io.File

@DisableCachingByDefault(because = "Produces console output only")
abstract class MetricsTask : CodeNavigatorTask() {

    @Option(option = "after", description = "Only consider commits after this date")
    @get:Internal
    var after: String? = null

    @Option(option = "top", description = "Max results per section")
    @get:Internal
    var top: String? = null

    @Option(option = "no-follow", description = "Disable git rename tracking")
    @get:Internal
    var noFollow: Boolean = false

    @Option(option = "package-filter", description = "Only include packages under this prefix")
    @get:Internal
    var packageFilter: String? = null

    @Option(option = "include-external", description = "Include dependencies on classes outside the project")
    @get:Internal
    var includeExternal: String? = null

    @Option(option = "exclude-annotated", description = "Exclude classes/methods bearing these annotations (simple names, comma-separated)")
    @get:Internal
    var excludeAnnotated: String? = null

    @Option(option = "treat-as-dead", description = "Treat framework-annotated code as potentially dead. Use ALL to remove all framework protections.")
    @get:Internal
    var treatAsDead: String? = null

    @Option(option = "root-package", description = "Deprecated: use package-filter instead. Only include packages under this prefix")
    @get:Internal
    var rootPackage: String? = null

    @Option(option = "scope", description = "Filter by source set: all (default), prod (production only), test (test only)")
    @get:Internal
    var scope: String? = null

    override fun taskOptionsMap(): Map<String, String?> = buildMap {
        after?.let { put("after", it) }
        top?.let { put("top", it) }
        if (noFollow) put("no-follow", "true")
        packageFilter?.let { put("package-filter", it) }
        includeExternal?.let { put("include-external", it) }
        excludeAnnotated?.let { put("exclude-annotated", it) }
        treatAsDead?.let { put("treat-as-dead", it) }
        rootPackage?.let { put("root-package", it) }
        scope?.let { put("scope", it) }
    }

    @TaskAction
    fun showMetrics() {
        val extension = project.codeNavigatorExtension()
        val props = extension.resolveProperties(TaskRegistry.METRICS.enhanceProperties(buildOptionsMap()))

        val config = MetricsConfig.parse(props)
        config.deprecations().forEach { logger.warn(it) }

        // Use DeadCodeConfig for consistent framework preset resolution
        val deadCodeConfig = DeadCodeConfig.parse(props)

        val taggedDirs = project.taggedClassDirectories()
        val cacheDir = File(project.layout.buildDirectory.asFile.get(), "cnav")
        val output = MetricsOrchestrator.run(config, deadCodeConfig, taggedDirs, loadPlanSteps(), cacheDir, project.projectDir)

        output.skippedFileWarning?.let { logger.warn(it) }
        val metrics = output.result

        logger.lifecycle(OutputWrapper.formatAndWrap(config.format) { format ->
    when (format) {
        OutputFormat.TEXT, OutputFormat.DIFF -> MetricsFormatter.format(metrics)
        OutputFormat.JSON -> MetricsFormatter.formatJson(metrics)
        OutputFormat.LLM -> MetricsFormatter.formatLlm(metrics)
    }
})
    }
}
