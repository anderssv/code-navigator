package no.f12.codenavigator.maven

import no.f12.codenavigator.config.OutputFormat
import no.f12.codenavigator.formatting.OutputWrapper
import no.f12.codenavigator.registry.TaskRegistry
import no.f12.codenavigator.analysis.GitLogRunner
import no.f12.codenavigator.navigation.report.ReportConfig
import no.f12.codenavigator.navigation.report.ReportFormatter
import no.f12.codenavigator.navigation.report.ReportOrchestrator
import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugins.annotations.Execute
import org.apache.maven.plugins.annotations.LifecyclePhase
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.project.MavenProject
import java.io.File

@Mojo(name = "report")
@Execute(phase = LifecyclePhase.COMPILE)
class ReportMojo : AbstractMojo() {

    @Parameter(defaultValue = "\${project}", readonly = true, required = true)
    private lateinit var project: MavenProject

    @Parameter(property = "format")
    private var format: String? = null


    @Parameter(property = "after")
    private var after: String? = null

    @Parameter(property = "top")
    private var top: String? = null

    @Parameter(property = "package-filter")
    private var packageFilter: String? = null

    @Parameter(property = "include-external")
    private var includeExternal: String? = null

    @Parameter(property = "no-follow")
    private var noFollow: Boolean = false

    @Parameter(property = "exclude-annotated")
    private var excludeAnnotated: String? = null

    @Parameter(property = "treat-as-dead")
    private var treatAsDead: String? = null

    @Parameter(property = "scope")
    private var scope: String? = null

    override fun execute() {
        project.checkStaleness(log)

        val props = TaskRegistry.REPORT.enhanceProperties(project.applyConfigDefaults(buildPropertyMap()))
        val config = ReportConfig.parse(props)
        val afterDate = TaskRegistry.AFTER.parseFrom(props)
        val followRenames = props["no-follow"] != "true"

        val taggedDirs = project.taggedClassDirectories()
        val classDirectories = taggedDirs.filter { config.scope.matchesSourceSet(it.second) }.map { it.first }

        if (classDirectories.isEmpty() || classDirectories.none { it.exists() }) {
            log.warn("Classes directory does not exist — run 'mvn compile' first.")
            return
        }

        val testClassesDir = File(project.build.testOutputDirectory)
        val testClassDirectories = if (testClassesDir.exists()) listOf(testClassesDir) else emptyList()

        val cacheDir = File(project.build.directory, "cnav")
        val reportFile = File(cacheDir, "skipped-files.txt")
        val commits = GitLogRunner.run(project.basedir, afterDate, followRenames = followRenames)

        val data = ReportOrchestrator.run(config, classDirectories, testClassDirectories, commits, cacheDir, reportFile)
        data.skippedFileWarning?.let { log.warn(it) }

        println(OutputWrapper.formatAndWrap(config.format) { format ->
            when (format) {
                OutputFormat.TEXT, OutputFormat.DIFF -> ReportFormatter.format(data)
                OutputFormat.JSON -> ReportFormatter.formatJson(data)
                OutputFormat.LLM -> ReportFormatter.formatLlm(data)
            }
        })
    }

    private fun buildPropertyMap(): Map<String, String?> = buildMap {
        format?.let { put("format", it) }
        after?.let { put("after", it) }
        top?.let { put("top", it) }
        packageFilter?.let { put("package-filter", it) }
        includeExternal?.let { put("include-external", it) }
        excludeAnnotated?.let { put("exclude-annotated", it) }
        treatAsDead?.let { put("treat-as-dead", it) }
        if (noFollow) put("no-follow", null)
        scope?.let { put("scope", it) }
    }
}
