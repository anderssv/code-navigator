package no.f12.codenavigator.gradle

import no.f12.codenavigator.config.OutputFormat

import no.f12.codenavigator.formatting.OutputWrapper
import no.f12.codenavigator.registry.TaskRegistry
import no.f12.codenavigator.navigation.relations.callgraph.CallGraphCache
import no.f12.codenavigator.navigation.deadcode.DeadCodeBaselineDiff
import no.f12.codenavigator.navigation.deadcode.DeadCodeBaselineDiffFormatter
import no.f12.codenavigator.navigation.deadcode.DeadCodeConfig
import no.f12.codenavigator.navigation.deadcode.DeadCodeFormatter
import no.f12.codenavigator.navigation.deadcode.DeadCodeOrchestrator
import no.f12.codenavigator.navigation.bytecode.SkippedFileReporter
import no.f12.codenavigator.navigation.types.Scope

import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import org.gradle.work.DisableCachingByDefault
import java.io.File

@DisableCachingByDefault(because = "Produces console output only")
abstract class DeadCodeTask : WorkspaceAnalysisTask() {

    @Option(option = "filter", description = "Only show results matching this regex")
    @get:Internal
    var filter: String? = null

    @Option(option = "exclude", description = "Exclude results matching this regex")
    @get:Internal
    var exclude: String? = null

    @Option(option = "classes-only", description = "Show only unreferenced classes, skip dead methods")
    @get:Internal
    var classesOnly: Boolean = false

    @Option(option = "exclude-annotated", description = "Exclude classes/methods bearing these annotations (simple names, comma-separated)")
    @get:Internal
    var excludeAnnotated: String? = null

    @Option(option = "scope", description = "Filter by source set: all (default), prod (production only), test (test only)")
    @get:Internal
    var scope: String? = null

    @Option(option = "treat-as-dead", description = "Treat framework-annotated code as potentially dead. Use ALL to remove all framework protections.")
    @get:Internal
    var treatAsDead: String? = null

    @Option(option = "baseline", description = "Path to a previous cnavDead JSON output. Shows diff: removed, remaining, and new dead code.")
    @get:Internal
    var baseline: String? = null

    @Option(option = "min-confidence", description = "Only show findings at or above this confidence level: high, medium, or low (default: low, show all).")
    @get:Internal
    var minConfidence: String? = null

    @Option(option = "include-suppressed", description = "Include findings annotated @Suppress(\"unused\") (excluded by default).")
    @get:Internal
    var includeSuppressed: Boolean = false

    override fun analysisOptionsMap(): Map<String, String?> = buildMap {
        filter?.let { put("filter", it) }
        exclude?.let { put("exclude", it) }
        if (classesOnly) put("classes-only", "true")
        excludeAnnotated?.let { put("exclude-annotated", it) }
        scope?.let { put("scope", it) }
        treatAsDead?.let { put("treat-as-dead", it) }
        baseline?.let { put("baseline", it) }
        minConfidence?.let { put("min-confidence", it) }
        if (includeSuppressed) put("include-suppressed", "true")
    }

    @TaskAction
    fun showDeadCode() {
        val config = DeadCodeConfig.parse(
            TaskRegistry.DEAD.enhanceProperties(buildOptionsMap()),
        )

        val workspace = resolveAnalysisWorkspace()
        val classDirectories = workspace.classDirectories(Scope.PROD)

        val cacheDir = File(project.layout.buildDirectory.asFile.get(), "cnav")
        val cacheFile = File(cacheDir, "call-graph.cache")
        val result = CallGraphCache.getOrBuild(cacheFile, classDirectories)
        val reportFile = File(cacheDir, "skipped-files.txt")
        SkippedFileReporter.report(result.skippedFiles, reportFile)?.let { logger.warn(it) }
        val graph = result.data

        val testClassDirectories = workspace.classDirectories(Scope.TEST).filter { it.exists() }
        val testGraph = if (testClassDirectories.isNotEmpty()) {
            CallGraphCache.getOrBuild(
                File(cacheDir, "test-call-graph.cache"),
                testClassDirectories,
            ).data
        } else {
            null
        }

        val dead = DeadCodeOrchestrator.findDeadCode(DeadCodeOrchestrator.DeadCodeInput(
            graph = graph,
            classDirectories = classDirectories,
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
            minConfidence = config.minConfidence,
            ignoreSuppress = config.ignoreSuppress,
        ))

        if (dead.isEmpty()) {
            logger.quiet(OutputWrapper.emptyResult(config.format, "No potential dead code found."))
            return
        }

        if (config.baseline != null) {
            val baselineFile = project.file(config.baseline)
            if (!baselineFile.exists()) {
                logger.quiet("Baseline file not found: ${baselineFile.absolutePath}")
                return
            }
            val baselineItems = DeadCodeBaselineDiff.parseBaseline(baselineFile.readText())
            val diff = DeadCodeBaselineDiff.compare(baselineItems, dead)
            logger.quiet(OutputWrapper.formatAndWrap(config.format) { format ->
    when (format) {
        OutputFormat.TEXT, OutputFormat.DIFF -> DeadCodeBaselineDiffFormatter.format(diff)
        OutputFormat.JSON -> DeadCodeBaselineDiffFormatter.formatJson(diff)
        OutputFormat.LLM -> DeadCodeBaselineDiffFormatter.format(diff)
    }
})
            return
        }

        logger.quiet(OutputWrapper.formatAndWrap(config.format) { format ->
    when (format) {
        OutputFormat.TEXT, OutputFormat.DIFF -> DeadCodeFormatter.format(dead, config.scope)
        OutputFormat.JSON -> DeadCodeFormatter.formatJson(dead, config.scope)
        OutputFormat.LLM -> DeadCodeFormatter.formatLlm(dead, config.scope)
    }
})
    }
}
