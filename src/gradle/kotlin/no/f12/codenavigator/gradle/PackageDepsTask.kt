package no.f12.codenavigator.gradle

import no.f12.codenavigator.JsonFormatter
import no.f12.codenavigator.LlmFormatter
import no.f12.codenavigator.config.OutputFormat
import no.f12.codenavigator.OutputWrapper
import no.f12.codenavigator.TaskRegistry
import no.f12.codenavigator.navigation.CallGraphCache
import no.f12.codenavigator.navigation.MethodRef
import no.f12.codenavigator.navigation.PackageDependencyBuilder
import no.f12.codenavigator.navigation.PackageDependencyFormatter
import no.f12.codenavigator.navigation.PackageDepsConfig
import no.f12.codenavigator.navigation.SkippedFileReporter

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.SourceSetContainer
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

        val sourceSets = project.extensions.getByType(SourceSetContainer::class.java)
        val mainSourceSet = sourceSets.getByName("main")
        val classDirectories = mainSourceSet.output.classesDirs.files.toList()

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

        val output = when (config.format) {
            OutputFormat.JSON -> JsonFormatter.formatPackageDeps(deps, packages, config.reverse)
            OutputFormat.LLM -> LlmFormatter.formatPackageDeps(deps, packages, config.reverse)
            OutputFormat.TEXT -> PackageDependencyFormatter.format(deps, packages, config.reverse)
        }
        logger.lifecycle(OutputWrapper.wrap(output, config.format))
    }
}
