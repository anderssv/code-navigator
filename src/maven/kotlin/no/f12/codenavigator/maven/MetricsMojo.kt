package no.f12.codenavigator.maven

import no.f12.codenavigator.config.OutputFormat

import no.f12.codenavigator.formatting.OutputWrapper
import no.f12.codenavigator.registry.TaskRegistry
import no.f12.codenavigator.navigation.deadcode.DeadCodeConfig
import no.f12.codenavigator.navigation.metrics.MetricsConfig
import no.f12.codenavigator.navigation.metrics.MetricsFormatter
import no.f12.codenavigator.navigation.metrics.MetricsOrchestrator
import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugins.annotations.Execute
import org.apache.maven.plugins.annotations.LifecyclePhase
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.project.MavenProject
import java.io.File

@Mojo(name = "metrics")
@Execute(phase = LifecyclePhase.COMPILE)
class MetricsMojo : AbstractMojo() {

    @Parameter(defaultValue = "\${project}", readonly = true, required = true)
    private lateinit var project: MavenProject

    @Parameter(property = "format")
    private var format: String? = null


    @Parameter(property = "after")
    private var after: String? = null

    @Parameter(property = "top")
    private var top: String? = null

    @Parameter(property = "root-package")
    private var rootPackage: String? = null

    @Parameter(property = "package-filter")
    private var packageFilter: String? = null

    @Parameter(property = "include-external")
    private var includeExternal: String? = null

    @Parameter(property = "no-follow")
    private var noFollow: Boolean = false

    @Parameter(property = "exclude-annotated")
    private var excludeAnnotated: String? = null

    @Parameter(property = "treat-as-dead")
    private var treatAsDead: String? = null

    @Parameter(property = "scope")
    private var scope: String? = null

    @Parameter(property = "plan-file")
    private var planFile: String? = null

    override fun execute() {
        project.checkStaleness(log)

        val props = TaskRegistry.METRICS.enhanceProperties(project.applyConfigDefaults(buildPropertyMap()))
        val config = MetricsConfig.parse(props)
        config.deprecations().forEach { log.warn(it) }

        // Use DeadCodeConfig for consistent framework preset resolution
        val deadCodeConfig = DeadCodeConfig.parse(props)

        val taggedDirs = project.taggedClassDirectories()
        val cacheDir = File(project.build.directory, "cnav")
        val output = MetricsOrchestrator.run(config, deadCodeConfig, taggedDirs, loadPlanSteps(planFile), cacheDir, project.basedir)

        output.skippedFileWarning?.let { log.warn(it) }
        val metrics = output.result

        println(OutputWrapper.formatAndWrap(config.format) { format ->
    when (format) {
        OutputFormat.TEXT, OutputFormat.DIFF -> MetricsFormatter.format(metrics)
        OutputFormat.JSON -> MetricsFormatter.formatJson(metrics)
        OutputFormat.LLM -> MetricsFormatter.formatLlm(metrics)
    }
})
    }

    private fun buildPropertyMap(): Map<String, String?> = buildMap {
        format?.let { put("format", it) }
        after?.let { put("after", it) }
        top?.let { put("top", it) }
        rootPackage?.let { put("root-package", it) }
        packageFilter?.let { put("package-filter", it) }
        includeExternal?.let { put("include-external", it) }
        excludeAnnotated?.let { put("exclude-annotated", it) }
        treatAsDead?.let { put("treat-as-dead", it) }
        if (noFollow) put("no-follow", null)
        scope?.let { put("scope", it) }
        planFile?.let { put("plan-file", it) }
    }
}
