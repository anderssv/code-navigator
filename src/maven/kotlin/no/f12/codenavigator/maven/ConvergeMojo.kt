package no.f12.codenavigator.maven

import no.f12.codenavigator.config.OutputFormat

import no.f12.codenavigator.formatting.OutputWrapper
import no.f12.codenavigator.registry.TaskRegistry
import no.f12.codenavigator.analysis.GitLogRunner
import no.f12.codenavigator.navigation.converge.ConvergeConfig
import no.f12.codenavigator.navigation.converge.ConvergeFormatter
import no.f12.codenavigator.navigation.converge.ConvergeOrchestrator
import no.f12.codenavigator.navigation.converge.ConvergeOutput
import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugins.annotations.Execute
import org.apache.maven.plugins.annotations.LifecyclePhase
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.project.MavenProject
import java.io.File

@Mojo(name = "converge")
@Execute(phase = LifecyclePhase.COMPILE)
class ConvergeMojo : AbstractMojo() {

    @Parameter(defaultValue = "\${project}", readonly = true, required = true)
    private lateinit var project: MavenProject

    @Parameter(property = "format")
    private var format: String? = null

    @Parameter(property = "mode")
    private var mode: String? = null

    @Parameter(property = "package-filter")
    private var packageFilter: String? = null

    @Parameter(property = "exclude")
    private var exclude: String? = null

    @Parameter(property = "after")
    private var after: String? = null

    @Parameter(property = "min-shared-revs")
    private var minSharedRevs: String? = null

    @Parameter(property = "min-coupling")
    private var minCoupling: String? = null

    @Parameter(property = "max-changeset-size")
    private var maxChangesetSize: String? = null

    @Parameter(property = "no-follow")
    private var noFollow: Boolean = false

    @Parameter(property = "top")
    private var top: String? = null

    @Parameter(property = "scope")
    private var scope: String? = null

    override fun execute() {
        project.checkStaleness(log)

        val config = ConvergeConfig.parse(TaskRegistry.CONVERGE.enhanceProperties(project.applyConfigDefaults(buildPropertyMap())))

        val taggedDirs = project.taggedClassDirectories()
        val classDirectories = taggedDirs.filter { config.scope.matchesSourceSet(it.second) }.map { it.first }

        if (classDirectories.isEmpty() || classDirectories.none { it.exists() }) {
            log.warn("Classes directory does not exist — run 'mvn compile' first.")
            return
        }

        val commits = GitLogRunner.run(project.basedir, config.after, followRenames = config.followRenames)
        val cacheFile = File(project.build.directory, "cnav/call-graph.cache")
        val reportFile = File(project.build.directory, "cnav/skipped-files.txt")
        val output = ConvergeOrchestrator.run(config, taggedDirs, commits, project.basedir, cacheFile, reportFile)

        val skippedFileWarning = when (output) {
            is ConvergeOutput.Intersect -> output.output.skippedFileWarning
            is ConvergeOutput.Risk -> output.output.skippedFileWarning
        }
        skippedFileWarning?.let { log.warn(it) }

        val isEmpty = when (output) {
            is ConvergeOutput.Intersect -> output.output.edges.isEmpty()
            is ConvergeOutput.Risk -> output.output.entries.isEmpty()
        }
        if (isEmpty) {
            println(OutputWrapper.emptyResult(config.format, "No converging signals found."))
            return
        }

        println(OutputWrapper.formatAndWrap(config.format) { format ->
    when (format) {
        OutputFormat.TEXT, OutputFormat.DIFF -> ConvergeFormatter.format(output)
        OutputFormat.JSON -> ConvergeFormatter.formatJson(output)
        OutputFormat.LLM -> ConvergeFormatter.formatLlm(output)
    }
})
    }

    private fun buildPropertyMap(): Map<String, String?> = buildMap {
        format?.let { put("format", it) }
        mode?.let { put("mode", it) }
        packageFilter?.let { put("package-filter", it) }
        exclude?.let { put("exclude", it) }
        after?.let { put("after", it) }
        minSharedRevs?.let { put("min-shared-revs", it) }
        minCoupling?.let { put("min-coupling", it) }
        maxChangesetSize?.let { put("max-changeset-size", it) }
        if (noFollow) put("no-follow", null)
        top?.let { put("top", it) }
        scope?.let { put("scope", it) }
    }
}
