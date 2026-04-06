package no.f12.codenavigator.maven

import no.f12.codenavigator.formatting.JsonFormatter
import no.f12.codenavigator.formatting.LlmFormatter
import no.f12.codenavigator.formatting.OutputWrapper
import no.f12.codenavigator.navigation.core.SkippedFileReporter
import no.f12.codenavigator.navigation.core.scanProjectClasses
import no.f12.codenavigator.navigation.dsm.DsmDependencyExtractor
import no.f12.codenavigator.navigation.dsm.LayerCheckConfig
import no.f12.codenavigator.navigation.dsm.LayerCheckResult
import no.f12.codenavigator.navigation.dsm.LayerChecker
import no.f12.codenavigator.navigation.dsm.LayerConfig
import no.f12.codenavigator.navigation.dsm.LayerFormatter
import no.f12.codenavigator.navigation.dsm.LayerInitGenerator
import no.f12.codenavigator.registry.TaskRegistry
import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugin.MojoFailureException
import org.apache.maven.plugins.annotations.Execute
import org.apache.maven.plugins.annotations.LifecyclePhase
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.project.MavenProject
import java.io.File

@Mojo(name = "layer-check")
@Execute(phase = LifecyclePhase.COMPILE)
class LayerCheckMojo : AbstractMojo() {

    @Parameter(defaultValue = "\${project}", readonly = true, required = true)
    private lateinit var project: MavenProject

    @Parameter(property = "format")
    private var format: String? = null

    @Parameter(property = "llm")
    private var llm: String? = null

    @Parameter(property = "config")
    private var config: String? = null

    @Parameter(property = "init")
    private var init: String? = null

    override fun execute() {
        val checkConfig = LayerCheckConfig.parse(TaskRegistry.LAYER_CHECK.enhanceProperties(buildPropertyMap()))

        val classDirectories = project.taggedClassDirectories().map { it.first }

        if (classDirectories.isEmpty() || classDirectories.none { it.exists() }) {
            log.warn("Classes directory does not exist — run 'mvn compile' first.")
            return
        }

        val projectClasses = scanProjectClasses(classDirectories)

        val extractResult = DsmDependencyExtractor.extract(classDirectories, projectClasses)
        val reportFile = File(project.build.directory, "cnav/skipped-files.txt")
        SkippedFileReporter.report(extractResult.skippedFiles, reportFile)?.let { log.warn(it) }
        val dependencies = extractResult.data

        if (checkConfig.init) {
            val configFile = File(project.basedir, checkConfig.configPath)
            val json = LayerInitGenerator.generateConfigJson(projectClasses.toList())
            configFile.writeText(json)
            val sampleClasses = projectClasses.sorted().toList()
            println(OutputWrapper.formatAndWrap(checkConfig.format,
                text = { LayerFormatter.formatInit(checkConfig.configPath, projectClasses.size, sampleClasses) },
                json = { JsonFormatter.formatLayerCheck(LayerCheckResult(emptyList(), emptySet())) },
                llm = { LayerFormatter.formatInit(checkConfig.configPath, projectClasses.size, sampleClasses) },
            ))
            return
        }

        val configFile = File(project.basedir, checkConfig.configPath)
        if (!configFile.exists()) {
            throw MojoFailureException("Layer config file not found: ${checkConfig.configPath}\nRun with -Dinit=true to generate a starter config.")
        }

        val layerConfig = LayerConfig.parse(configFile.readText())
        val result = LayerChecker.check(layerConfig, dependencies)

        println(OutputWrapper.formatAndWrap(checkConfig.format,
            text = { LayerFormatter.format(result) },
            json = { JsonFormatter.formatLayerCheck(result) },
            llm = { LlmFormatter.formatLayerCheck(result) },
        ))

        if (result.violations.isNotEmpty()) {
            throw MojoFailureException("${result.violations.size} layer violation(s) found. See output above for details.")
        }
    }

    private fun buildPropertyMap(): Map<String, String?> = buildMap {
        format?.let { put("format", it) }
        llm?.let { put("llm", it) }
        config?.let { put("config", it) }
        init?.let { put("init", it) }
    }
}
