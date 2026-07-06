package no.f12.codenavigator.maven

import no.f12.codenavigator.config.OutputFormat

import no.f12.codenavigator.formatting.JsonFormatter
import no.f12.codenavigator.formatting.LlmFormatter
import no.f12.codenavigator.formatting.OutputWrapper
import no.f12.codenavigator.registry.TaskRegistry
import no.f12.codenavigator.navigation.relations.callgraph.FindUsagesConfig
import no.f12.codenavigator.navigation.relations.callgraph.FindUsagesOrchestrator
import no.f12.codenavigator.navigation.types.GroupBy
import no.f12.codenavigator.navigation.relations.callgraph.UsageFormatter
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

    @Parameter(property = "scope")
    private var scope: String? = null

    @Parameter(property = "filter-synthetic")
    private var filterSynthetic: String? = null

    @Parameter(property = "group-by")
    private var groupBy: String? = null

    @Parameter(property = "raw")
    private var raw: String? = null

    @Parameter(property = "include-impls")
    private var includeImpls: String? = null

    override fun execute() {
        project.checkStaleness(log)

        val config = try {
            FindUsagesConfig.parse(TaskRegistry.FIND_USAGES.enhanceProperties(project.applyConfigDefaults(buildPropertyMap())))
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

        val cacheDir = File(project.build.directory, "cnav")

        val output = FindUsagesOrchestrator.run(config, taggedDirs, cacheDir)
        output.skippedFileWarning?.let { log.warn(it) }

        if (output.usages.isEmpty() && output.implementations.isEmpty()) {
            val target = UsageFormatter.noResultsTarget(config.ownerClass, config.method, config.field, config.type)
            val hints = UsageFormatter.noResultsHints(config.ownerClass, config.method, config.field, config.type)
            println(OutputWrapper.emptyResult(config.format, "No usages found for '$target'.", hints))
            return
        }

        val smartResult = output.toSmartResult()

        println(OutputWrapper.formatAndWrap(config.format) { format ->
            when (format) {
                OutputFormat.TEXT, OutputFormat.DIFF -> when {
                    config.groupBy == GroupBy.FILE -> UsageFormatter.formatSummary(output.usages)
                    config.raw -> UsageFormatter.format(output.usages)
                    else -> UsageFormatter.formatSmartUsages(smartResult, output.collapsed)
                }
                OutputFormat.JSON -> when {
                    config.groupBy == GroupBy.FILE -> JsonFormatter.formatUsagesSummary(output.usages)
                    config.raw -> JsonFormatter.formatUsages(output.usages)
                    else -> JsonFormatter.formatSmartUsages(smartResult, output.collapsed)
                }
                OutputFormat.LLM -> when {
                    config.groupBy == GroupBy.FILE -> LlmFormatter.formatUsagesSummary(output.usages)
                    config.raw -> LlmFormatter.formatUsages(output.usages)
                    else -> LlmFormatter.formatSmartUsages(smartResult, output.collapsed)
                }
            }
        })
    }

    private fun buildPropertyMap(): Map<String, String?> = buildMap {
        format?.let { put("format", it) }
        ownerClass?.let { put("owner-class", it) }
        method?.let { put("method", it) }
        field?.let { put("field", it) }
        type?.let { put("type", it) }
        outsidePackage?.let { put("outside-package", it) }
        scope?.let { put("scope", it) }
        filterSynthetic?.let { put("filter-synthetic", it) }
        groupBy?.let { put("group-by", it) }
        raw?.let { put("raw", it) }
        includeImpls?.let { put("include-impls", it) }
    }
}
