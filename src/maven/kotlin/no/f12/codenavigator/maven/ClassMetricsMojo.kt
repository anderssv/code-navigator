package no.f12.codenavigator.maven

import no.f12.codenavigator.config.OutputFormat
import no.f12.codenavigator.formatting.JsonFormatter
import no.f12.codenavigator.formatting.LlmFormatter
import no.f12.codenavigator.formatting.OutputWrapper
import no.f12.codenavigator.navigation.classmetrics.ClassMetricsConfig
import no.f12.codenavigator.navigation.classmetrics.ClassMetricsFormatter
import no.f12.codenavigator.navigation.classmetrics.ClassMetricsOrchestrator
import no.f12.codenavigator.registry.TaskRegistry
import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugins.annotations.Execute
import org.apache.maven.plugins.annotations.LifecyclePhase
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.project.MavenProject
import java.io.File

@Mojo(name = "class-metrics")
@Execute(phase = LifecyclePhase.COMPILE)
class ClassMetricsMojo : AbstractMojo() {

    @Parameter(defaultValue = "\${project}", readonly = true, required = true)
    private lateinit var project: MavenProject

    @Parameter(property = "format")
    private var format: String? = null

    @Parameter(property = "min-methods")
    private var minMethods: String? = null

    @Parameter(property = "min-tcc")
    private var minTcc: String? = null

    @Parameter(property = "max-wmc")
    private var maxWmc: String? = null

    @Parameter(property = "max-cbo")
    private var maxCbo: String? = null

    @Parameter(property = "scope")
    private var scope: String? = null

    override fun execute() {
        project.checkStaleness(log)

        val config = ClassMetricsConfig.parse(TaskRegistry.CLASS_METRICS.enhanceProperties(project.applyConfigDefaults(buildPropertyMap())))

        val taggedDirs = project.taggedClassDirectories()
        val classDirectories = taggedDirs.filter { config.scope.matchesSourceSet(it.second) }.map { it.first }

        if (classDirectories.isEmpty() || classDirectories.none { it.exists() }) {
            log.warn("Classes directory does not exist — run 'mvn compile' first.")
            return
        }

        val reportFile = File(project.build.directory, "cnav/skipped-files.txt")
        val output = ClassMetricsOrchestrator.run(config, classDirectories, reportFile)

        output.skippedFileWarning?.let { log.warn(it) }

        if (output.results.isEmpty()) {
            println(OutputWrapper.emptyResult(config.format, "No matching classes found."))
            return
        }

        println(OutputWrapper.formatAndWrap(config.format) { format ->
    when (format) {
        OutputFormat.TEXT, OutputFormat.DIFF -> ClassMetricsFormatter.format(output.results)
        OutputFormat.JSON -> JsonFormatter.formatClassMetrics(output.results)
        OutputFormat.LLM -> LlmFormatter.formatClassMetrics(output.results)
    }
})
    }

    private fun buildPropertyMap(): Map<String, String?> = buildMap {
        format?.let { put("format", it) }
        minMethods?.let { put("min-methods", it) }
        minTcc?.let { put("min-tcc", it) }
        maxWmc?.let { put("max-wmc", it) }
        maxCbo?.let { put("max-cbo", it) }
        scope?.let { put("scope", it) }
    }
}
