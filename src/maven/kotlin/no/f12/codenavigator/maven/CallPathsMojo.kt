package no.f12.codenavigator.maven

import no.f12.codenavigator.config.OutputFormat
import no.f12.codenavigator.formatting.OutputWrapper
import no.f12.codenavigator.navigation.branches.BranchScan
import no.f12.codenavigator.navigation.branches.BranchScanner
import no.f12.codenavigator.navigation.relations.callgraph.CallPathConfig
import no.f12.codenavigator.navigation.relations.callgraph.CallPathFormatter
import no.f12.codenavigator.navigation.relations.callgraph.CallPathOrchestrator
import no.f12.codenavigator.registry.BuildTool
import no.f12.codenavigator.registry.TaskRegistry
import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugin.MojoFailureException
import org.apache.maven.plugins.annotations.Execute
import org.apache.maven.plugins.annotations.LifecyclePhase
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.project.MavenProject
import java.io.File

@Mojo(name = "call-paths")
@Execute(phase = LifecyclePhase.COMPILE)
class CallPathsMojo : AbstractMojo() {

    @Parameter(defaultValue = "\${project}", readonly = true, required = true)
    private lateinit var project: MavenProject

    @Parameter(property = "format")
    private var format: String? = null

    @Parameter(property = "from")
    private var from: String? = null

    @Parameter(property = "from-annotation")
    private var fromAnnotation: String? = null

    @Parameter(property = "to")
    private var to: String? = null

    @Parameter(property = "guard")
    private var guard: String? = null

    @Parameter(property = "guard-depth")
    private var guardDepth: String? = null

    @Parameter(property = "maxdepth")
    private var maxdepth: String? = null

    @Parameter(property = "top")
    private var top: String? = null

    @Parameter(property = "project-only")
    private var projectOnly: String? = null

    @Parameter(property = "scope")
    private var scope: String? = null

    @Parameter(property = "branches")
    private var branches: String? = null

    override fun execute() {
        project.checkStaleness(log)
        val taskDef = TaskRegistry.CALL_PATHS
        val config = try {
            CallPathConfig.parse(taskDef.enhanceProperties(project.applyConfigDefaults(buildPropertyMap())))
        } catch (e: IllegalArgumentException) {
            throw MojoFailureException("${e.message}\n${taskDef.usageHint(BuildTool.MAVEN)}")
        }

        val taggedDirs = project.taggedClassDirectories()
        if (taggedDirs.isEmpty()) {
            log.warn("Classes directory does not exist: ${File(project.build.outputDirectory)} — run 'mvn compile' first.")
            return
        }

        val output = CallPathOrchestrator.run(config, taggedDirs, File(project.build.directory, "cnav"), BranchScan(project.allSourceRoots(), BranchScanner::scan))
        output.skippedFileWarning?.let { log.warn(it) }

        val report = output.report
        if (report == null) {
            println(OutputWrapper.emptyResult(config.format, output.noMatch ?: "No call paths"))
            return
        }
        println(
            OutputWrapper.formatAndWrap(config.format) { format ->
                when (format) {
                    OutputFormat.TEXT, OutputFormat.DIFF -> CallPathFormatter.formatText(report)
                    OutputFormat.JSON -> CallPathFormatter.formatJson(report)
                    OutputFormat.LLM -> CallPathFormatter.formatLlm(report)
                }
            },
        )
    }

    private fun buildPropertyMap(): Map<String, String?> = buildMap {
        format?.let { put("format", it) }
        from?.let { put("from", it) }
        fromAnnotation?.let { put("from-annotation", it) }
        to?.let { put("to", it) }
        guard?.let { put("guard", it) }
        guardDepth?.let { put("guard-depth", it) }
        maxdepth?.let { put("maxdepth", it) }
        top?.let { put("top", it) }
        projectOnly?.let { put("project-only", it) }
        scope?.let { put("scope", it) }
        branches?.let { put("branches", it) }
    }
}
