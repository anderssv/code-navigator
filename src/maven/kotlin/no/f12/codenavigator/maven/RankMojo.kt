package no.f12.codenavigator.maven

import no.f12.codenavigator.JsonFormatter
import no.f12.codenavigator.LlmFormatter
import no.f12.codenavigator.OutputFormat
import no.f12.codenavigator.OutputWrapper
import no.f12.codenavigator.navigation.CallGraphBuilder
import no.f12.codenavigator.navigation.RankConfig
import no.f12.codenavigator.navigation.RankFormatter
import no.f12.codenavigator.navigation.SkippedFileReporter
import no.f12.codenavigator.navigation.TypeRanker
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

    @Parameter(property = "projectonly")
    private var projectonly: String? = null

    override fun execute() {
        val classesDir = File(project.build.outputDirectory)
        if (!classesDir.exists()) {
            log.warn("Classes directory does not exist: $classesDir — run 'mvn compile' first.")
            return
        }

        val config = RankConfig.parse(buildPropertyMap())

        val result = CallGraphBuilder.build(listOf(classesDir))
        val reportFile = File(project.build.directory, "cnav/skipped-files.txt")
        SkippedFileReporter.report(result.skippedFiles, reportFile)?.let { log.warn(it) }
        val graph = result.data

        val ranked = TypeRanker.rank(graph, top = config.top, projectOnly = config.projectOnly)

        if (ranked.isEmpty()) {
            println("No ranked types found.")
            return
        }

        val output = when (config.format) {
            OutputFormat.JSON -> JsonFormatter.formatRank(ranked)
            OutputFormat.LLM -> LlmFormatter.formatRank(ranked)
            OutputFormat.TEXT -> RankFormatter.format(ranked)
        }
        println(OutputWrapper.wrap(output, config.format))
    }

    private fun buildPropertyMap(): Map<String, String?> = buildMap {
        format?.let { put("format", it) }
        llm?.let { put("llm", it) }
        top?.let { put("top", it) }
        projectonly?.let { put("projectonly", it) }
    }
}
