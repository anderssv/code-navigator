package no.f12.codenavigator.gradle

import no.f12.codenavigator.config.OutputFormat

import no.f12.codenavigator.formatting.OutputWrapper
import no.f12.codenavigator.registry.TaskRegistry
import no.f12.codenavigator.navigation.bytecode.SkippedFileReporter
import no.f12.codenavigator.navigation.dsm.DsmDependencyExtractor
import no.f12.codenavigator.navigation.dsm.WhyDependsBuilder
import no.f12.codenavigator.navigation.dsm.WhyDependsConfig
import no.f12.codenavigator.navigation.dsm.WhyDependsFormatter
import no.f12.codenavigator.navigation.types.PackageName
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import org.gradle.work.DisableCachingByDefault
import java.io.File

@DisableCachingByDefault(because = "Produces console output only")
abstract class WhyDependsTask : CodeNavigatorTask() {

    @Option(option = "from-package", description = "Source package (dot-separated)")
    @get:Internal
    var fromPackage: String? = null

    @Option(option = "to-package", description = "Target package (dot-separated)")
    @get:Internal
    var toPackage: String? = null

    @Option(option = "scope", description = "Filter by source set: all (default), prod (production only), test (test only)")
    @get:Internal
    var scope: String? = null

    override fun taskOptionsMap(): Map<String, String?> = buildMap {
        fromPackage?.let { put("from-package", it) }
        toPackage?.let { put("to-package", it) }
        scope?.let { put("scope", it) }
    }

    @TaskAction
    fun showWhyDepends() {
        val config = WhyDependsConfig.parse(
            TaskRegistry.WHY_DEPENDS.enhanceProperties(buildOptionsMap()),
        )

        val taggedDirs = project.taggedClassDirectories()
        val filteredDirs = taggedDirs.filter { config.scope.matchesSourceSet(it.second) }
        val classDirectories = filteredDirs.map { it.first }

        val extractResult = DsmDependencyExtractor.extract(classDirectories, PackageName(""))
        val reportFile = File(project.layout.buildDirectory.asFile.get(), "cnav/skipped-files.txt")
        SkippedFileReporter.report(extractResult.skippedFiles, reportFile)?.let { logger.warn(it) }

        val result = WhyDependsBuilder.build(
            dependencies = extractResult.data,
            fromPackage = PackageName(config.fromPackage),
            toPackage = PackageName(config.toPackage),
        )

        if (result.edges.isEmpty()) {
            val hints = WhyDependsFormatter.noResultsHints(result.fromPackage, result.toPackage)
            logger.quiet(OutputWrapper.emptyResult(config.format, "No dependencies found from '${config.fromPackage}' to '${config.toPackage}'.", hints))
            return
        }

        logger.quiet(OutputWrapper.formatAndWrap(config.format) { format ->
    when (format) {
        OutputFormat.TEXT, OutputFormat.DIFF -> WhyDependsFormatter.format(result)
        OutputFormat.JSON -> WhyDependsFormatter.format(result)
        OutputFormat.LLM -> WhyDependsFormatter.format(result)
    }
})
    }
}
