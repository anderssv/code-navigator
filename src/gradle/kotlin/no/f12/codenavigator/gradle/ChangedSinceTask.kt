package no.f12.codenavigator.gradle

import no.f12.codenavigator.formatting.JsonFormatter
import no.f12.codenavigator.formatting.LlmFormatter
import no.f12.codenavigator.formatting.OutputWrapper
import no.f12.codenavigator.registry.TaskRegistry
import no.f12.codenavigator.navigation.core.Scope
import no.f12.codenavigator.navigation.core.SourceSetResolver
import no.f12.codenavigator.navigation.callgraph.CallGraphCache
import no.f12.codenavigator.navigation.changedsince.ChangedSinceBuilder
import no.f12.codenavigator.navigation.changedsince.ChangedSinceConfig
import no.f12.codenavigator.navigation.changedsince.ChangedSinceFormatter
import no.f12.codenavigator.navigation.changedsince.GitDiffRunner
import no.f12.codenavigator.navigation.changedsince.SourceFileResolver
import no.f12.codenavigator.navigation.classinfo.ClassIndexCache
import no.f12.codenavigator.navigation.core.SkippedFileReporter
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.File

@DisableCachingByDefault(because = "Produces console output only")
abstract class ChangedSinceTask : DefaultTask() {

    @TaskAction
    fun showChangedSince() {
        val config = ChangedSinceConfig.parse(
            project.buildPropertyMap(TaskRegistry.CHANGED_SINCE),
        )

        if (config.ref == null) {
            logger.error("Required parameter 'ref' not set. Usage: -Pref=<git-ref> (branch, tag, or commit SHA)")
            return
        }

        val taggedDirs = project.taggedClassDirectories()
        val resolver = SourceSetResolver.from(taggedDirs)

        val gitPaths = GitDiffRunner.run(project.projectDir, config.ref)
        if (gitPaths.isEmpty()) {
            logger.lifecycle(OutputWrapper.emptyResult(config.format, "No changed files since ${config.ref}."))
            return
        }

        val classIndexFile = File(project.layout.buildDirectory.asFile.get(), "cnav/class-index.cache")
        val classInfos = ClassIndexCache.getOrBuild(classIndexFile, resolver.classDirectories).data

        val resolution = SourceFileResolver.resolve(gitPaths, classInfos)

        if (resolution.resolved.isEmpty()) {
            if (resolution.unresolved.isNotEmpty()) {
                val msg = "${resolution.unresolved.size} changed file(s), none mapped to project classes:\n${resolution.unresolved.joinToString("\n") { "  $it" }}"
                logger.lifecycle(OutputWrapper.emptyResult(config.format, msg))
            } else {
                logger.lifecycle(OutputWrapper.emptyResult(config.format, "No changed files since ${config.ref}."))
            }
            return
        }

        val cacheFile = File(project.layout.buildDirectory.asFile.get(), "cnav/call-graph.cache")
        val result = CallGraphCache.getOrBuild(cacheFile, resolver.classDirectories)
        val reportFile = File(project.layout.buildDirectory.asFile.get(), "cnav/skipped-files.txt")
        SkippedFileReporter.report(result.skippedFiles, reportFile)?.let { logger.warn(it) }
        val graph = result.data

        val allImpacts = ChangedSinceBuilder.build(
            changedClasses = resolution.resolved.keys,
            graph = graph,
            projectOnly = config.projectOnly,
        )
        val impacts = allImpacts.filter { resolver.sourceSetOf(it.className)?.let { ss -> config.scope.matchesSourceSet(ss) } ?: true }

        logger.lifecycle(
            OutputWrapper.formatAndWrap(
                config.format,
                text = { ChangedSinceFormatter.format(impacts, resolution.unresolved) },
                json = { JsonFormatter.formatChangedSince(impacts, resolution.unresolved) },
                llm = { LlmFormatter.formatChangedSince(impacts, resolution.unresolved) },
            ),
        )
    }
}
