package no.f12.codenavigator.gradle

import no.f12.codenavigator.config.OutputFormat

import no.f12.codenavigator.formatting.OutputWrapper
import no.f12.codenavigator.registry.TaskRegistry
import no.f12.codenavigator.navigation.types.Scope
import no.f12.codenavigator.navigation.bytecode.SourceSetResolver
import no.f12.codenavigator.navigation.relations.callgraph.CallGraphCache
import no.f12.codenavigator.navigation.changedsince.ChangedSinceBuilder
import no.f12.codenavigator.navigation.changedsince.ChangedSinceConfig
import no.f12.codenavigator.navigation.changedsince.ChangedSinceFormatter
import no.f12.codenavigator.navigation.changedsince.GitDiffRunner
import no.f12.codenavigator.navigation.changedsince.SourceFileResolver
import no.f12.codenavigator.navigation.classinfo.ClassIndexCache
import no.f12.codenavigator.navigation.bytecode.SkippedFileReporter
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import org.gradle.work.DisableCachingByDefault
import java.io.File

@DisableCachingByDefault(because = "Produces console output only")
abstract class ChangedSinceTask : CodeNavigatorTask() {

    @Option(option = "ref", description = "Git ref to compare against (branch, tag, or commit SHA)")
    @get:Internal
    var ref: String? = null

    @Option(option = "project-only", description = "Hide JDK/stdlib/library classes (default: on)")
    @get:Internal
    var projectOnly: String? = null

    @Option(option = "scope", description = "Filter by source set: all (default), prod (production only), test (test only)")
    @get:Internal
    var scope: String? = null

    override fun taskOptionsMap(): Map<String, String?> = buildMap {
        ref?.let { put("ref", it) }
        projectOnly?.let { put("project-only", it) }
        scope?.let { put("scope", it) }
    }

    @TaskAction
    fun showChangedSince() {
        val config = ChangedSinceConfig.parse(
            TaskRegistry.CHANGED_SINCE.enhanceProperties(buildOptionsMap()),
        )

        if (config.ref == null) {
            logger.error("Required parameter 'ref' not set. Usage: --ref=<git-ref> (branch, tag, or commit SHA)")
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
            OutputWrapper.formatAndWrap(config.format) { format ->
    when (format) {
        OutputFormat.TEXT, OutputFormat.DIFF -> ChangedSinceFormatter.format(impacts, resolution.unresolved)
        OutputFormat.JSON -> ChangedSinceFormatter.formatJson(impacts, resolution.unresolved)
        OutputFormat.LLM -> ChangedSinceFormatter.formatLlm(impacts, resolution.unresolved)
    }
},
        )
    }
}
