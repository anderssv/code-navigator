package no.f12.codenavigator.gradle

import no.f12.codenavigator.JsonFormatter
import no.f12.codenavigator.LlmFormatter
import no.f12.codenavigator.OutputFormat
import no.f12.codenavigator.OutputWrapper
import no.f12.codenavigator.navigation.CallGraphCache
import no.f12.codenavigator.navigation.MethodRef
import no.f12.codenavigator.navigation.PackageDependencyBuilder
import no.f12.codenavigator.navigation.PackageDependencyFormatter
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
        val pattern = project.findProperty("package")?.toString()
        val projectOnly = project.findProperty("projectonly")?.toString()?.toBoolean() ?: false
        val reverse = project.findProperty("reverse")?.toString()?.toBoolean() ?: false
        val format = project.outputFormat()

        val sourceSets = project.extensions.getByType(SourceSetContainer::class.java)
        val mainSourceSet = sourceSets.getByName("main")
        val classDirectories = mainSourceSet.output.classesDirs.files.toList()

        val cacheFile = File(project.layout.buildDirectory.asFile.get(), "cnav/call-graph.cache")
        val result = CallGraphCache.getOrBuild(cacheFile, classDirectories)
        val reportFile = File(project.layout.buildDirectory.asFile.get(), "cnav/skipped-files.txt")
        SkippedFileReporter.report(result.skippedFiles, reportFile)?.let { logger.warn(it) }
        val graph = result.data

        val filter: ((MethodRef) -> Boolean)? =
            if (projectOnly) graph.projectClassFilter() else null

        val deps = PackageDependencyBuilder.build(graph, filter)

        val packages = if (pattern != null) {
            val matches = deps.findPackages(pattern)
            if (matches.isEmpty()) {
                logger.lifecycle("No packages found matching '$pattern'")
                return
            }
            matches
        } else {
            deps.allPackages()
        }

        val output = when (format) {
            OutputFormat.JSON -> JsonFormatter.formatPackageDeps(deps, packages, reverse)
            OutputFormat.LLM -> LlmFormatter.formatPackageDeps(deps, packages, reverse)
            OutputFormat.TEXT -> PackageDependencyFormatter.format(deps, packages, reverse)
        }
        logger.lifecycle(OutputWrapper.wrap(output, format))
    }
}
