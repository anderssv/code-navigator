package no.f12.codenavigator.gradle

import no.f12.codenavigator.JsonFormatter
import no.f12.codenavigator.LlmFormatter
import no.f12.codenavigator.OutputWrapper
import no.f12.codenavigator.TaskRegistry
import no.f12.codenavigator.navigation.SourceSet
import no.f12.codenavigator.navigation.callgraph.CallGraphCache
import no.f12.codenavigator.navigation.callgraph.MethodRef
import no.f12.codenavigator.navigation.dsm.PackageDependencyBuilder
import no.f12.codenavigator.navigation.dsm.PackageDependencyFormatter
import no.f12.codenavigator.navigation.dsm.PackageDepsConfig
import no.f12.codenavigator.navigation.SkippedFileReporter

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.File

@DisableCachingByDefault(because = "Produces console output only")
abstract class PackageDepsTask : DefaultTask() {

    @TaskAction
    fun showDeps() {
        val config = PackageDepsConfig.parse(
            project.buildPropertyMap(TaskRegistry.PACKAGE_DEPS),
        )

        val taggedDirs = project.taggedClassDirectories()
        val filteredDirs = when {
            config.prodOnly -> taggedDirs.filter { it.second == SourceSet.MAIN }
            config.testOnly -> taggedDirs.filter { it.second == SourceSet.TEST }
            else -> taggedDirs
        }
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
                logger.lifecycle("No packages found matching '${config.packagePattern}'")
                return
            }
            matches
        } else {
            deps.allPackages()
        }

        logger.lifecycle(OutputWrapper.formatAndWrap(config.format,
            text = { PackageDependencyFormatter.format(deps, packages, config.reverse) },
            json = { JsonFormatter.formatPackageDeps(deps, packages, config.reverse) },
            llm = { LlmFormatter.formatPackageDeps(deps, packages, config.reverse) },
        ))
    }
}
