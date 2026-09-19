package no.f12.codenavigator.maven

import no.f12.codenavigator.config.OutputFormat
import no.f12.codenavigator.formatting.OutputWrapper
import no.f12.codenavigator.navigation.testalignment.TestAlignmentFormatter
import no.f12.codenavigator.navigation.testalignment.TestAlignmentGuidance
import no.f12.codenavigator.navigation.testalignment.TestAlignmentOrchestrator
import no.f12.codenavigator.navigation.testalignment.TestAlignmentTaskConfig
import no.f12.codenavigator.registry.TaskRegistry
import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugin.MojoFailureException
import org.apache.maven.plugins.annotations.Execute
import org.apache.maven.plugins.annotations.LifecyclePhase
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.project.MavenProject
import java.io.File

@Mojo(name = "test-alignment")
@Execute(phase = LifecyclePhase.TEST_COMPILE)
class TestAlignmentMojo : AbstractMojo() {

    @Parameter(defaultValue = "\${project}", readonly = true, required = true)
    private lateinit var project: MavenProject

    @Parameter(property = "format")
    private var format: String? = null

    @Parameter(property = "exclude")
    private var exclude: String? = null

    @Parameter(property = "fail-on-violation")
    private var failOnViolation: String? = null

    @Parameter(property = "max-violations")
    private var maxViolations: String? = null

    override fun execute() {
        project.checkStaleness(log)

        val config = TestAlignmentTaskConfig.parse(
            TaskRegistry.TEST_ALIGNMENT.enhanceProperties(project.applyConfigDefaults(buildPropertyMap())),
        )

        val cacheDir = File(project.build.directory, "cnav")
        val output = TestAlignmentOrchestrator.run(
            config,
            project.taggedClassDirectories(),
            cacheDir,
            File(cacheDir, "skipped-files.txt"),
        )
        output.skippedFileWarning?.let { log.warn(it) }

        if (output.noTestClasses) {
            println(
                OutputWrapper.wrapWithGuidance(
                    "No compiled test classes found — run 'mvn test-compile' first.",
                    config.format,
                    TestAlignmentGuidance.GUIDANCE,
                ),
            )
            return
        }

        val result = output.result ?: return

        println(
            OutputWrapper.wrapWithGuidance(
                when (config.format) {
                    OutputFormat.JSON -> TestAlignmentFormatter.formatJson(result)
                    OutputFormat.LLM -> TestAlignmentFormatter.formatLlm(result)
                    else -> TestAlignmentFormatter.formatText(result)
                },
                config.format,
                TestAlignmentGuidance.GUIDANCE,
            ),
        )

        if (config.failOnViolation && result.violationCount > config.maxViolations) {
            throw MojoFailureException(
                "Test alignment check failed: ${result.violationCount} violation(s) exceed the allowed maximum of ${config.maxViolations}. " +
                    "See output above for details.",
            )
        }
    }

    private fun buildPropertyMap(): Map<String, String?> = buildMap {
        format?.let { put("format", it) }
        exclude?.let { put("exclude", it) }
        failOnViolation?.let { put("fail-on-violation", it) }
        maxViolations?.let { put("max-violations", it) }
    }
}
