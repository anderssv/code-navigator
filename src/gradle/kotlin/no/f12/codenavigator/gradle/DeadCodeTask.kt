package no.f12.codenavigator.gradle

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

import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import org.gradle.work.DisableCachingByDefault
import java.io.File

@DisableCachingByDefault(because = "Produces console output only")
abstract class DeadCodeTask : CodeNavigatorTask() {

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

    override fun taskOptionsMap(): Map<String, String?> = buildMap {
        filter?.let { put("filter", it) }
        exclude?.let { put("exclude", it) }
        if (classesOnly) put("classes-only", "true")
        excludeAnnotated?.let { put("exclude-annotated", it) }
        scope?.let { put("scope", it) }
        treatAsDead?.let { put("treat-as-dead", it) }
        baseline?.let { put("baseline", it) }
    }

    @TaskAction
    fun showDeadCode() {
        val config = DeadCodeConfig.parse(
            TaskRegistry.DEAD.enhanceProperties(buildOptionsMap()),
        )

        val sourceSets = project.extensions.getByType(SourceSetContainer::class.java)
        val mainSourceSet = sourceSets.getByName("main")
        val classDirectories = mainSourceSet.output.classesDirs.files.toList()

        val cacheDir = File(project.layout.buildDirectory.asFile.get(), "cnav")
        val cacheFile = File(cacheDir, "call-graph.cache")
        val result = CallGraphCache.getOrBuild(cacheFile, classDirectories)
        val reportFile = File(cacheDir, "skipped-files.txt")
        SkippedFileReporter.report(result.skippedFiles, reportFile)?.let { logger.warn(it) }
        val graph = result.data

        val testSourceSet = sourceSets.findByName("test")
        val testClassDirectories = testSourceSet?.output?.classesDirs?.files?.filter { it.exists() }?.toList() ?: emptyList()
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
        ))

        if (dead.isEmpty()) {
            logger.lifecycle(OutputWrapper.emptyResult(config.format, "No potential dead code found."))
            return
        }

        if (config.baseline != null) {
            val baselineFile = project.file(config.baseline)
            if (!baselineFile.exists()) {
                logger.lifecycle("Baseline file not found: ${baselineFile.absolutePath}")
                return
            }
            val baselineItems = DeadCodeBaselineDiff.parseBaseline(baselineFile.readText())
            val diff = DeadCodeBaselineDiff.compare(baselineItems, dead)
            logger.lifecycle(OutputWrapper.formatAndWrap(config.format,
                text = { DeadCodeBaselineDiffFormatter.format(diff) },
                json = { DeadCodeBaselineDiffFormatter.formatJson(diff) },
                llm = { DeadCodeBaselineDiffFormatter.format(diff) },
            ))
            return
        }

        logger.lifecycle(OutputWrapper.formatAndWrap(config.format,
            text = { DeadCodeFormatter.format(dead, config.scope) },
            json = { JsonFormatter.formatDead(dead, config.scope) },
            llm = { LlmFormatter.formatDead(dead, config.scope) },
        ))
    }
}
