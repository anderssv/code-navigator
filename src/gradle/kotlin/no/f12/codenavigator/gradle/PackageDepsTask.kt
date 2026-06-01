package no.f12.codenavigator.gradle

import no.f12.codenavigator.formatting.JsonFormatter
import no.f12.codenavigator.formatting.LlmFormatter
import no.f12.codenavigator.formatting.OutputWrapper
import no.f12.codenavigator.registry.TaskRegistry
import no.f12.codenavigator.navigation.types.Scope
import no.f12.codenavigator.navigation.relations.callgraph.CallGraphCache
import no.f12.codenavigator.navigation.relations.callgraph.MethodRef
import no.f12.codenavigator.navigation.dsm.PackageDependencyBuilder
import no.f12.codenavigator.navigation.dsm.PackageDependencyFormatter
import no.f12.codenavigator.navigation.dsm.PackageDepsConfig
import no.f12.codenavigator.navigation.bytecode.SkippedFileReporter

import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import org.gradle.work.DisableCachingByDefault
import java.io.File

@DisableCachingByDefault(because = "Produces console output only")
abstract class PackageDepsTask : CodeNavigatorTask() {

    @Option(option = "package", description = "Filter packages by regex")
    @get:Internal
    var pkg: String? = null

    @Option(option = "project-only", description = "Hide JDK/stdlib/library classes (default: on)")
    @get:Internal
    var projectOnly: String? = null

    @Option(option = "reverse", description = "Show reverse dependencies")
    @get:Internal
    var reverse: String? = null

    @Option(option = "scope", description = "Filter by source set: all (default), prod (production only), test (test only)")
    @get:Internal
    var scope: String? = null

    override fun taskOptionsMap(): Map<String, String?> = buildMap {
        pkg?.let { put("package", it) }
        projectOnly?.let { put("project-only", it) }
        reverse?.let { put("reverse", it) }
        scope?.let { put("scope", it) }
    }

    @TaskAction
    fun showDeps() {
        val config = PackageDepsConfig.parse(
            TaskRegistry.PACKAGE_DEPS.enhanceProperties(buildOptionsMap()),
        )

        val taggedDirs = project.taggedClassDirectories()
        val filteredDirs = taggedDirs.filter { config.scope.matchesSourceSet(it.second) }
        val classDirectories = filteredDirs.map { it.first }

        val cacheFile = File(project.layout.buildDirectory.asFile.get(), "cnav/call-graph.cache")
        val result = CallGraphCache.getOrBuild(cacheFile, classDirectories)
        val reportFile = File(project.layout.buildDirectory.asFile.get(), "cnav/skipped-files.txt")
        SkippedFileReporter.report(result.skippedFiles, reportFile)?.let { logger.warn(it) }
        val graph = result.data

        val filter: ((MethodRef) -> Boolean)? =
            if (config.projectOnly) graph.projectClassFilter() else null

        val deps = PackageDependencyBuilder.build(graph, filter)

        val packages = if (config.packagePattern != null) {
            val matches = deps.findPackages(config.packagePattern)
            if (matches.isEmpty()) {
                logger.lifecycle(OutputWrapper.emptyResult(config.format, "No packages found matching '${config.packagePattern}'"))
                return
            }
            matches
        } else {
            val all = deps.allPackages()
            if (all.isEmpty()) {
                val packageCount = graph.projectClasses().map { it.packageName() }.distinct().size
                val hints = PackageDependencyFormatter.noResultsHints(packageCount)
                logger.lifecycle(OutputWrapper.emptyResult(config.format, "No inter-package dependencies found.", hints))
                return
            }
            all
        }

        logger.lifecycle(OutputWrapper.formatAndWrap(config.format,
            text = { PackageDependencyFormatter.format(deps, packages, config.reverse) },
            json = { JsonFormatter.formatPackageDeps(deps, packages, config.reverse) },
            llm = { LlmFormatter.formatPackageDeps(deps, packages, config.reverse) },
        ))
    }
}
