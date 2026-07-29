package no.f12.codenavigator.gradle

import no.f12.codenavigator.config.OutputFormat
import no.f12.codenavigator.formatting.OutputWrapper
import no.f12.codenavigator.registry.TaskRegistry
import no.f12.codenavigator.analysis.GitLogRunner
import no.f12.codenavigator.navigation.report.ReportConfig
import no.f12.codenavigator.navigation.report.ReportFormatter
import no.f12.codenavigator.navigation.report.ReportOrchestrator
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import org.gradle.work.DisableCachingByDefault
import java.io.File

@DisableCachingByDefault(because = "Produces console output only")
abstract class ReportTask : CodeNavigatorTask() {

    @Option(option = "package-filter", description = "Only include packages under this prefix")
    @get:Internal
    var packageFilter: String? = null

    @Option(option = "include-external", description = "Include dependencies on classes outside the project")
    @get:Internal
    var includeExternal: String? = null

    @Option(option = "top", description = "Max results")
    @get:Internal
    var top: String? = null

    @Option(option = "after", description = "Only consider commits after this date")
    @get:Internal
    var after: String? = null

    @Option(option = "no-follow", description = "Disable git rename tracking")
    @get:Internal
    var noFollow: Boolean = false

    @Option(option = "exclude-annotated", description = "Exclude classes/methods bearing these annotations (simple names, comma-separated)")
    @get:Internal
    var excludeAnnotated: String? = null

    @Option(option = "treat-as-dead", description = "Treat framework-annotated code as potentially dead. Use ALL to remove all framework protections.")
    @get:Internal
    var treatAsDead: String? = null

    @Option(option = "scope", description = "Filter by source set: all (default), prod (production only), test (test only)")
    @get:Internal
    var scope: String? = null

    override fun taskOptionsMap(): Map<String, String?> = buildMap {
        packageFilter?.let { put("package-filter", it) }
        includeExternal?.let { put("include-external", it) }
        top?.let { put("top", it) }
        after?.let { put("after", it) }
        if (noFollow) put("no-follow", "true")
        excludeAnnotated?.let { put("exclude-annotated", it) }
        treatAsDead?.let { put("treat-as-dead", it) }
        scope?.let { put("scope", it) }
    }

    @TaskAction
    fun generateReport() {
        val extension = project.codeNavigatorExtension()
        val props = extension.resolveProperties(TaskRegistry.REPORT.enhanceProperties(buildOptionsMap()))

        val config = ReportConfig.parse(props)
        val afterVal = TaskRegistry.AFTER.parseFrom(props)
        val followRenames = props["no-follow"] != "true"

        val taggedDirs = project.taggedClassDirectories()
        val classDirectories = taggedDirs.filter { config.scope.matchesSourceSet(it.second) }.map { it.first }

        val sourceSets = project.extensions.getByType(SourceSetContainer::class.java)
        val testClassDirectories = sourceSets.findByName("test")?.output?.classesDirs?.files?.filter { it.exists() }?.toList() ?: emptyList()

        val cacheDir = File(project.layout.buildDirectory.asFile.get(), "cnav")
        val reportFile = File(cacheDir, "skipped-files.txt")
        val commits = GitLogRunner.run(project.projectDir, afterVal, followRenames = followRenames)

        val data = ReportOrchestrator.run(config, classDirectories, testClassDirectories, commits, cacheDir, reportFile)
        data.skippedFileWarning?.let { logger.warn(it) }

        logger.quiet(OutputWrapper.formatAndWrap(config.format) { format ->
            when (format) {
                OutputFormat.TEXT, OutputFormat.DIFF -> ReportFormatter.format(data)
                OutputFormat.JSON -> ReportFormatter.formatJson(data)
                OutputFormat.LLM -> ReportFormatter.formatLlm(data)
            }
        })
    }
}
