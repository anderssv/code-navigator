package no.f12.codenavigator.maven

import no.f12.codenavigator.formatting.JsonFormatter
import no.f12.codenavigator.formatting.LlmFormatter
import no.f12.codenavigator.formatting.OutputWrapper
import no.f12.codenavigator.registry.TaskRegistry
import no.f12.codenavigator.navigation.callgraph.FindUsagesConfig
import no.f12.codenavigator.navigation.SkippedFileReporter
import no.f12.codenavigator.navigation.callgraph.UsageFormatter
import no.f12.codenavigator.navigation.callgraph.UsageScanner
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

    @Parameter(property = "owner-class")
    private var ownerClass: String? = null

    @Parameter(property = "method")
    private var method: String? = null

    @Parameter(property = "field")
    private var field: String? = null

    @Parameter(property = "type")
    private var type: String? = null

    @Parameter(property = "outside-package")
    private var outsidePackage: String? = null

    @Parameter(property = "prod-only")
    private var prodOnly: String? = null

    @Parameter(property = "test-only")
    private var testOnly: String? = null

    override fun execute() {
        val config = try {
            FindUsagesConfig.parse(TaskRegistry.FIND_USAGES.enhanceProperties(buildPropertyMap()))
        } catch (e: IllegalArgumentException) {
            throw MojoFailureException(
                "${e.message}\n" +
                    "Usage: mvn cnav:find-usages -Downer-class=<class> [-Dmethod=<name>] [-Dfield=<name>]\n" +
                    "       mvn cnav:find-usages -Dtype=<class>",
            )
        }

        val taggedDirs = project.taggedClassDirectories()
        if (taggedDirs.isEmpty()) {
            log.warn("Classes directory does not exist: ${File(project.build.outputDirectory)} — run 'mvn compile' first.")
            return
        }

        val result = UsageScanner.scanTagged(taggedDirs, ownerClass = config.ownerClass, method = config.method, field = config.field, type = config.type)
        val reportFile = File(project.build.directory, "cnav/skipped-files.txt")
        SkippedFileReporter.report(result.skippedFiles, reportFile)?.let { log.warn(it) }
        val afterPackageFilter = UsageScanner.filterOutsidePackage(result.data, config.outsidePackage)
        val usages = config.filterBySourceSet(afterPackageFilter)

        if (usages.isEmpty()) {
            val target = UsageFormatter.noResultsTarget(config.ownerClass, config.method, config.field, config.type)
            val hints = UsageFormatter.noResultsHints(config.ownerClass, config.method, config.field, config.type)
            println(OutputWrapper.emptyResult(config.format, "No usages found for '$target'.", hints))
            return
        }

        println(OutputWrapper.formatAndWrap(config.format,
            text = { UsageFormatter.format(usages) },
            json = { JsonFormatter.formatUsages(usages) },
            llm = { LlmFormatter.formatUsages(usages) },
        ))
    }

    private fun buildPropertyMap(): Map<String, String?> = buildMap {
        format?.let { put("format", it) }
        llm?.let { put("llm", it) }
        ownerClass?.let { put("owner-class", it) }
        method?.let { put("method", it) }
        field?.let { put("field", it) }
        type?.let { put("type", it) }
        outsidePackage?.let { put("outside-package", it) }
        prodOnly?.let { put("prod-only", it) }
        testOnly?.let { put("test-only", it) }
    }
}
