package no.f12.codenavigator.maven

import no.f12.codenavigator.config.OutputFormat
import no.f12.codenavigator.formatting.OutputWrapper
import no.f12.codenavigator.registry.TaskRegistry
import no.f12.codenavigator.navigation.testcoupling.TestCouplingFormatter
import no.f12.codenavigator.navigation.testcoupling.TestCouplingGuidance
import no.f12.codenavigator.navigation.testcoupling.TestCouplingOrchestrator
import no.f12.codenavigator.navigation.testcoupling.TestCouplingTaskConfig
import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugins.annotations.Execute
import org.apache.maven.plugins.annotations.LifecyclePhase
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.project.MavenProject
import java.io.File

@Mojo(name = "test-coupling")
@Execute(phase = LifecyclePhase.TEST_COMPILE)
class TestCouplingMojo : AbstractMojo() {

    @Parameter(defaultValue = "\${project}", readonly = true, required = true)
    private lateinit var project: MavenProject

    @Parameter(property = "format")
    private var format: String? = null


    @Parameter(property = "ports")
    private var ports: String? = null

    @Parameter(property = "detail")
    private var detail: String? = null

    @Parameter(property = "exclude")
    private var exclude: String? = null

    @Parameter(property = "scope")
    private var scope: String? = null

    override fun execute() {
        project.checkStaleness(log)

        val config = TestCouplingTaskConfig.parse(TaskRegistry.TEST_COUPLING.enhanceProperties(project.applyConfigDefaults(buildPropertyMap())))

        val taggedDirs = project.taggedClassDirectories()
        val classDirectories = taggedDirs.map { it.first }

        if (classDirectories.isEmpty() || classDirectories.none { it.exists() }) {
            log.warn("Classes directory does not exist — run 'mvn test-compile' first.")
            return
        }

        val cacheDir = File(project.build.directory, "cnav")
        val reportFile = File(cacheDir, "skipped-files.txt")

        val output = TestCouplingOrchestrator.run(config, taggedDirs, cacheDir, reportFile)

        output.skippedFileWarning?.let { log.warn(it) }

        if (output.noPortsFound) {
            val guidance = TestCouplingGuidance.GUIDANCE
            println(OutputWrapper.wrapWithGuidance(
                "No interfaces matching '${config.ports.pattern}' found. Adjust -Dports to match your port interface names.",
                config.format,
                guidance,
            ))
            return
        }

        val result = output.result
        if (result == null || result.violations.isEmpty()) {
            println(OutputWrapper.wrapWithGuidance(
                "No TTTD violations found. All test classes use domain-oriented setup.",
                config.format,
                TestCouplingGuidance.GUIDANCE,
            ))
            return
        }

        val guidance = TestCouplingGuidance.GUIDANCE
        println(OutputWrapper.wrapWithGuidance(
            when (config.format) {
                OutputFormat.TEXT, OutputFormat.DIFF -> TestCouplingFormatter.formatText(result)
                OutputFormat.JSON -> TestCouplingFormatter.formatText(result)
                OutputFormat.LLM -> TestCouplingFormatter.formatLlm(result)
            },
            config.format,
            guidance,
        ))
    }

    private fun buildPropertyMap(): Map<String, String?> = buildMap {
        format?.let { put("format", it) }
        ports?.let { put("ports", it) }
        detail?.let { put("detail", it) }
        exclude?.let { put("exclude", it) }
        scope?.let { put("scope", it) }
    }
}
