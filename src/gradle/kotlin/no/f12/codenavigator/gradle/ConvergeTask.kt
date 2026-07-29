package no.f12.codenavigator.gradle

import no.f12.codenavigator.config.OutputFormat

import no.f12.codenavigator.formatting.OutputWrapper
import no.f12.codenavigator.registry.TaskRegistry
import no.f12.codenavigator.analysis.GitLogRunner
import no.f12.codenavigator.navigation.converge.ConvergeConfig
import no.f12.codenavigator.navigation.converge.ConvergeFormatter
import no.f12.codenavigator.navigation.converge.ConvergeOrchestrator
import no.f12.codenavigator.navigation.converge.ConvergeOutput

import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import org.gradle.work.DisableCachingByDefault
import java.io.File

@DisableCachingByDefault(because = "Produces console output only")
abstract class ConvergeTask : CodeNavigatorTask() {

    @Option(option = "mode", description = "Analysis mode: intersect (default) or risk")
    @get:Internal
    var mode: String? = null

    @Option(option = "package-filter", description = "Only include packages under this prefix")
    @get:Internal
    var packageFilter: String? = null

    @Option(option = "exclude", description = "Drop packages/classes whose name contains a match for this regex from analysis entirely (e.g. a DI composition root or test infrastructure)")
    @get:Internal
    var exclude: String? = null

    @Option(option = "after", description = "Only consider commits after this date")
    @get:Internal
    var after: String? = null

    @Option(option = "min-shared-revs", description = "Min shared commits")
    @get:Internal
    var minSharedRevs: String? = null

    @Option(option = "min-coupling", description = "Min coupling degree %")
    @get:Internal
    var minCoupling: String? = null

    @Option(option = "max-changeset-size", description = "Skip commits touching more files")
    @get:Internal
    var maxChangesetSize: String? = null

    @Option(option = "no-follow", description = "Disable git rename tracking")
    @get:Internal
    var noFollow: Boolean = false

    @Option(option = "top", description = "Max results")
    @get:Internal
    var top: String? = null

    @Option(option = "scope", description = "Filter by source set: all (default), prod (production only), test (test only)")
    @get:Internal
    var scope: String? = null

    override fun taskOptionsMap(): Map<String, String?> = buildMap {
        mode?.let { put("mode", it) }
        packageFilter?.let { put("package-filter", it) }
        exclude?.let { put("exclude", it) }
        after?.let { put("after", it) }
        minSharedRevs?.let { put("min-shared-revs", it) }
        minCoupling?.let { put("min-coupling", it) }
        maxChangesetSize?.let { put("max-changeset-size", it) }
        if (noFollow) put("no-follow", "true")
        top?.let { put("top", it) }
        scope?.let { put("scope", it) }
    }

    @TaskAction
    fun showConverge() {
        val config = ConvergeConfig.parse(
            TaskRegistry.CONVERGE.enhanceProperties(buildOptionsMap()),
        )

        val taggedDirs = project.taggedClassDirectories()
        val commits = GitLogRunner.run(project.projectDir, config.after, followRenames = config.followRenames)
        val cacheFile = File(project.layout.buildDirectory.asFile.get(), "cnav/call-graph.cache")
        val reportFile = File(project.layout.buildDirectory.asFile.get(), "cnav/skipped-files.txt")
        val output = ConvergeOrchestrator.run(config, taggedDirs, commits, project.projectDir, cacheFile, reportFile)

        val skippedFileWarning = when (output) {
            is ConvergeOutput.Intersect -> output.output.skippedFileWarning
            is ConvergeOutput.Risk -> output.output.skippedFileWarning
        }
        skippedFileWarning?.let { logger.warn(it) }

        val isEmpty = when (output) {
            is ConvergeOutput.Intersect -> output.output.edges.isEmpty()
            is ConvergeOutput.Risk -> output.output.entries.isEmpty()
        }
        if (isEmpty) {
            logger.quiet(OutputWrapper.emptyResult(config.format, "No converging signals found."))
            return
        }

        logger.quiet(OutputWrapper.formatAndWrap(config.format) { format ->
    when (format) {
        OutputFormat.TEXT, OutputFormat.DIFF -> ConvergeFormatter.format(output)
        OutputFormat.JSON -> ConvergeFormatter.formatJson(output)
        OutputFormat.LLM -> ConvergeFormatter.formatLlm(output)
    }
})
    }
}
