package no.f12.codenavigator.gradle

import no.f12.codenavigator.formatting.JsonFormatter
import no.f12.codenavigator.formatting.LlmFormatter
import no.f12.codenavigator.formatting.OutputWrapper
import no.f12.codenavigator.registry.TaskRegistry
import no.f12.codenavigator.navigation.callgraph.CallGraphCache
import no.f12.codenavigator.navigation.deadcode.DeadCodeConfig
import no.f12.codenavigator.navigation.deadcode.DeadCodeFormatter
import no.f12.codenavigator.navigation.deadcode.DeadCodeOrchestrator
import no.f12.codenavigator.navigation.core.SkippedFileReporter

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.File

@DisableCachingByDefault(because = "Produces console output only")
abstract class DeadCodeTask : DefaultTask() {

    @TaskAction
    fun showDeadCode() {
        val config = DeadCodeConfig.parse(
            project.buildPropertyMap(TaskRegistry.DEAD),
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

        logger.lifecycle(OutputWrapper.formatAndWrap(config.format,
            text = { DeadCodeFormatter.format(dead, config.scope) },
            json = { JsonFormatter.formatDead(dead, config.scope) },
            llm = { LlmFormatter.formatDead(dead, config.scope) },
        ))
    }
}
