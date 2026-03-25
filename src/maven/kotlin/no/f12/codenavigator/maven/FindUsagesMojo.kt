package no.f12.codenavigator.maven

import no.f12.codenavigator.JsonFormatter
import no.f12.codenavigator.LlmFormatter
import no.f12.codenavigator.OutputFormat
import no.f12.codenavigator.OutputWrapper
import no.f12.codenavigator.navigation.FindUsagesConfig
import no.f12.codenavigator.navigation.SkippedFileReporter
import no.f12.codenavigator.navigation.UsageFormatter
import no.f12.codenavigator.navigation.UsageScanner
import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugin.MojoFailureException
import org.apache.maven.plugins.annotations.Execute
import org.apache.maven.plugins.annotations.LifecyclePhase
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.project.MavenProject
import java.io.File

@Mojo(name = "find-usages")
@Execute(phase = LifecyclePhase.COMPILE)
class FindUsagesMojo : AbstractMojo() {

    @Parameter(defaultValue = "\${project}", readonly = true, required = true)
    private lateinit var project: MavenProject

    @Parameter(property = "format")
    private var format: String? = null

    @Parameter(property = "llm")
    private var llm: String? = null

    @Parameter(property = "ownerClass")
    private var ownerClass: String? = null

    @Parameter(property = "method")
    private var method: String? = null

    @Parameter(property = "type")
    private var type: String? = null

    @Parameter(property = "outside-package")
    private var outsidePackage: String? = null

    override fun execute() {
        val config = try {
            FindUsagesConfig.parse(buildPropertyMap())
        } catch (e: IllegalArgumentException) {
            throw MojoFailureException(
                "Missing required property. Provide either 'ownerClass' or 'type'.\n" +
                    "Usage: mvn cnav:find-usages -DownerClass=<class> [-Dmethod=<name>]\n" +
                    "       mvn cnav:find-usages -Dtype=<class>",
            )
        }

        val classesDir = File(project.build.outputDirectory)
        if (!classesDir.exists()) {
            log.warn("Classes directory does not exist: $classesDir — run 'mvn compile' first.")
            return
        }

        val result = UsageScanner.scan(listOf(classesDir), ownerClass = config.ownerClass, method = config.method, type = config.type)
        val reportFile = File(project.build.directory, "cnav/skipped-files.txt")
        SkippedFileReporter.report(result.skippedFiles, reportFile)?.let { log.warn(it) }
        val usages = UsageScanner.filterOutsidePackage(result.data, config.outsidePackage)

        if (usages.isEmpty()) {
            println(UsageFormatter.noResultsGuidance(config.ownerClass, config.method, config.type))
            return
        }

        val output = when (config.format) {
            OutputFormat.JSON -> JsonFormatter.formatUsages(usages)
            OutputFormat.LLM -> LlmFormatter.formatUsages(usages)
            OutputFormat.TEXT -> UsageFormatter.format(usages)
        }
        println(OutputWrapper.wrap(output, config.format))
    }

    private fun buildPropertyMap(): Map<String, String?> = buildMap {
        format?.let { put("format", it) }
        llm?.let { put("llm", it) }
        ownerClass?.let { put("ownerClass", it) }
        method?.let { put("method", it) }
        type?.let { put("type", it) }
        outsidePackage?.let { put("outside-package", it) }
    }
}
