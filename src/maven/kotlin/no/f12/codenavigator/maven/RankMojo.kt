package no.f12.codenavigator.maven

import no.f12.codenavigator.formatting.JsonFormatter
import no.f12.codenavigator.formatting.LlmFormatter
import no.f12.codenavigator.formatting.OutputWrapper
import no.f12.codenavigator.registry.TaskRegistry
import no.f12.codenavigator.navigation.callgraph.CallGraphCache
import no.f12.codenavigator.navigation.core.SkippedFileReporter
import no.f12.codenavigator.navigation.rank.RankConfig
import no.f12.codenavigator.navigation.rank.RankFormatter
import no.f12.codenavigator.navigation.rank.TypeRanker
import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugins.annotations.Execute
import org.apache.maven.plugins.annotations.LifecyclePhase
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.project.MavenProject
import java.io.File

@Mojo(name = "rank")
@Execute(phase = LifecyclePhase.COMPILE)
class RankMojo : AbstractMojo() {

    @Parameter(defaultValue = "\${project}", readonly = true, required = true)
    private lateinit var project: MavenProject

    @Parameter(property = "format")
    private var format: String? = null

    @Parameter(property = "llm")
    private var llm: String? = null

    @Parameter(property = "top")
    private var top: String? = null

    @Parameter(property = "project-only")
    private var projectOnly: String? = null

    @Parameter(property = "collapse-lambdas")
    private var collapseLambdas: String? = null

    @Parameter(property = "scope")
    private var scope: String? = null

    override fun execute() {
        val taggedDirs = project.taggedClassDirectories()
        if (taggedDirs.isEmpty()) {
            log.warn("Classes directory does not exist: ${File(project.build.outputDirectory)} — run 'mvn compile' first.")
            return
        }

        val config = RankConfig.parse(TaskRegistry.RANK.enhanceProperties(buildPropertyMap()))

        val result = CallGraphCache.getOrBuildTagged(File(project.build.directory, "cnav/call-graph.cache"), taggedDirs)
        val reportFile = File(project.build.directory, "cnav/skipped-files.txt")
        SkippedFileReporter.report(result.skippedFiles, reportFile)?.let { log.warn(it) }
        val graph = result.data

        val ranked = TypeRanker.rank(graph, top = config.top, projectOnly = config.projectOnly, collapseLambdas = config.collapseLambdas)
        val filtered = ranked.filter { graph.sourceSetOf(it.className)?.let { ss -> config.scope.matchesSourceSet(ss) } ?: true }

        if (filtered.isEmpty()) {
            println("No ranked types found.")
            return
        }

        println(OutputWrapper.formatAndWrap(config.format,
            text = { RankFormatter.format(filtered) },
            json = { JsonFormatter.formatRank(filtered) },
            llm = { LlmFormatter.formatRank(filtered) },
        ))
    }

    private fun buildPropertyMap(): Map<String, String?> = buildMap {
        format?.let { put("format", it) }
        llm?.let { put("llm", it) }
        top?.let { put("top", it) }
        projectOnly?.let { put("project-only", it) }
        collapseLambdas?.let { put("collapse-lambdas", it) }
        scope?.let { put("scope", it) }
    }
}
