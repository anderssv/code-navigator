package no.f12.codenavigator.maven

import no.f12.codenavigator.config.OutputFormat
import no.f12.codenavigator.formatting.OutputWrapper
import no.f12.codenavigator.navigation.ambient.AmbientFormatter
import no.f12.codenavigator.navigation.ambient.AmbientGuidance
import no.f12.codenavigator.navigation.ambient.AmbientOrchestrator
import no.f12.codenavigator.navigation.ambient.AmbientTaskConfig
import no.f12.codenavigator.registry.TaskRegistry
import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugin.MojoFailureException
import org.apache.maven.plugins.annotations.Execute
import org.apache.maven.plugins.annotations.LifecyclePhase
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.project.MavenProject
import java.io.File

@Mojo(name = "ambient")
@Execute(phase = LifecyclePhase.COMPILE)
class AmbientMojo : AbstractMojo() {

    @Parameter(defaultValue = "\${project}", readonly = true, required = true)
    private lateinit var project: MavenProject

    @Parameter(property = "format")
    private var format: String? = null

    @Parameter(property = "domain")
    private var domain: String? = null

    @Parameter(property = "categories")
    private var categories: String? = null

    @Parameter(property = "detail")
    private var detail: String? = null

    @Parameter(property = "exclude")
    private var exclude: String? = null

    @Parameter(property = "scope")
    private var scope: String? = null

    @Parameter(property = "fail-on-violation")
    private var failOnViolation: String? = null

    @Parameter(property = "max-violations")
    private var maxViolations: String? = null

    override fun execute() {
        project.checkStaleness(log)

        val config = AmbientTaskConfig.parse(
            TaskRegistry.AMBIENT.enhanceProperties(project.applyConfigDefaults(buildPropertyMap())),
        )

        val taggedDirs = project.taggedClassDirectories()
        val classDirectories = taggedDirs.map { it.first }

        if (classDirectories.isEmpty() || classDirectories.none { it.exists() }) {
            log.warn("Classes directory does not exist — run 'mvn compile' first.")
            return
        }

        val cacheDir = File(project.build.directory, "cnav")
        val reportFile = File(cacheDir, "skipped-files.txt")

        val output = AmbientOrchestrator.run(config, taggedDirs, reportFile, project.basedir)

        output.skippedFileWarning?.let { log.warn(it) }

        if (output.subjectsUnavailable) {
            println(OutputWrapper.wrapWithGuidance(
                "cnav:rings could not derive a layered hexagon for this project, so there is no ring 0 to check. " +
                    "Pass -Ddomain=<regex> to name the domain classes directly.",
                config.format,
                AmbientGuidance.GUIDANCE,
            ))
            return
        }

        val result = output.result
        if (result == null || result.all.isEmpty()) {
            println(OutputWrapper.wrapWithGuidance(AmbientFormatter.NO_FINDINGS, config.format, AmbientGuidance.GUIDANCE))
            return
        }

        println(OutputWrapper.wrapWithGuidance(
            when (config.format) {
                OutputFormat.LLM -> AmbientFormatter.formatLlm(result)
                OutputFormat.JSON -> AmbientFormatter.formatJson(result)
                OutputFormat.TEXT, OutputFormat.DIFF -> AmbientFormatter.formatText(result, config.detail)
            },
            config.format,
            AmbientGuidance.GUIDANCE,
        ))

        if (config.failOnViolation && result.actionable.size > config.maxViolations) {
            throw MojoFailureException(
                "cnav:ambient found ${result.actionable.size} finding(s), exceeding --max-violations=${config.maxViolations}",
            )
        }
    }

    private fun buildPropertyMap(): Map<String, String?> = buildMap {
        format?.let { put("format", it) }
        domain?.let { put("domain", it) }
        categories?.let { put("categories", it) }
        detail?.let { put("detail", it) }
        exclude?.let { put("exclude", it) }
        scope?.let { put("scope", it) }
        failOnViolation?.let { put("fail-on-violation", it) }
        maxViolations?.let { put("max-violations", it) }
    }
}
