package no.f12.codenavigator.maven

import no.f12.codenavigator.config.OutputFormat

import no.f12.codenavigator.formatting.JsonFormatter
import no.f12.codenavigator.formatting.LlmFormatter
import no.f12.codenavigator.formatting.OutputWrapper
import no.f12.codenavigator.registry.TaskRegistry
import no.f12.codenavigator.navigation.relations.callgraph.CallGraphCache
import no.f12.codenavigator.navigation.deadcode.DeadCodeBaselineDiff
import no.f12.codenavigator.navigation.deadcode.DeadCodeBaselineDiffFormatter
import no.f12.codenavigator.navigation.deadcode.DeadCodeConfig
import no.f12.codenavigator.navigation.deadcode.DeadCodeFormatter
import no.f12.codenavigator.navigation.deadcode.DeadCodeOrchestrator
import no.f12.codenavigator.navigation.bytecode.SkippedFileReporter
import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugins.annotations.Execute
import org.apache.maven.plugins.annotations.LifecyclePhase
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.project.MavenProject
import java.io.File

@Mojo(name = "dead")
@Execute(phase = LifecyclePhase.TEST_COMPILE)
class DeadCodeMojo : AbstractMojo() {

    @Parameter(defaultValue = "\${project}", readonly = true, required = true)
    private lateinit var project: MavenProject

    @Parameter(property = "format")
    private var format: String? = null


    @Parameter(property = "filter")
    private var filter: String? = null

    @Parameter(property = "exclude")
    private var exclude: String? = null

    @Parameter(property = "classes-only")
    private var classesOnly: String? = null

    @Parameter(property = "exclude-annotated")
    private var excludeAnnotated: String? = null

    @Parameter(property = "scope")
    private var scope: String? = null

    @Parameter(property = "treat-as-dead")
    private var treatAsDead: String? = null

    @Parameter(property = "baseline")
    private var baseline: String? = null

    override fun execute() {
        project.checkStaleness(log)

        val classesDir = File(project.build.outputDirectory)
        if (!classesDir.exists()) {
            log.warn("Classes directory does not exist: $classesDir — run 'mvn compile' first.")
            return
        }

        val config = DeadCodeConfig.parse(TaskRegistry.DEAD.enhanceProperties(buildPropertyMap()))

        val cacheDir = File(project.build.directory, "cnav")
        val result = CallGraphCache.getOrBuild(File(cacheDir, "call-graph.cache"), listOf(classesDir))
        val reportFile = File(cacheDir, "skipped-files.txt")
        SkippedFileReporter.report(result.skippedFiles, reportFile)?.let { log.warn(it) }
        val graph = result.data

        val testClassesDir = File(project.build.testOutputDirectory)
        val testGraph = if (testClassesDir.exists()) {
            CallGraphCache.getOrBuild(File(cacheDir, "test-call-graph.cache"), listOf(testClassesDir)).data
        } else {
            null
        }

        val dead = DeadCodeOrchestrator.findDeadCode(DeadCodeOrchestrator.DeadCodeInput(
            graph = graph,
            classDirectories = listOf(classesDir),
            testGraph = testGraph,
            excludeAnnotated = config.excludeAnnotated.toSet(),
            modifierAnnotated = config.modifierAnnotated.toSet(),
            supertypeEntryPoints = config.supertypeEntryPoints,
            receiverTypeEntryPoints = config.receiverTypeEntryPoints,
            scope = config.scope,
            filter = config.filter,
            exclude = config.exclude,
            classesOnly = config.classesOnly,
            cacheDir = cacheDir,
        ))

        if (dead.isEmpty()) {
            println("No potential dead code found.")
            return
        }

        if (config.baseline != null) {
            val baselineFile = File(config.baseline!!)
            if (!baselineFile.exists()) {
                log.warn("Baseline file not found: ${baselineFile.absolutePath}")
                return
            }
            val baselineItems = DeadCodeBaselineDiff.parseBaseline(baselineFile.readText())
            val diff = DeadCodeBaselineDiff.compare(baselineItems, dead)
            println(OutputWrapper.formatAndWrap(config.format) { format ->
    when (format) {
        OutputFormat.TEXT, OutputFormat.DIFF -> DeadCodeBaselineDiffFormatter.format(diff)
        OutputFormat.JSON -> DeadCodeBaselineDiffFormatter.formatJson(diff)
        OutputFormat.LLM -> DeadCodeBaselineDiffFormatter.format(diff)
    }
})
            return
        }

        println(OutputWrapper.formatAndWrap(config.format) { format ->
    when (format) {
        OutputFormat.TEXT, OutputFormat.DIFF -> DeadCodeFormatter.format(dead, config.scope)
        OutputFormat.JSON -> JsonFormatter.formatDead(dead, config.scope)
        OutputFormat.LLM -> LlmFormatter.formatDead(dead, config.scope)
    }
})
    }

    private fun buildPropertyMap(): Map<String, String?> = buildMap {
        format?.let { put("format", it) }
        filter?.let { put("filter", it) }
        exclude?.let { put("exclude", it) }
        classesOnly?.let { put("classes-only", it) }
        excludeAnnotated?.let { put("exclude-annotated", it) }
        scope?.let { put("scope", it) }
        treatAsDead?.let { put("treat-as-dead", it) }
        baseline?.let { put("baseline", it) }
    }
}
