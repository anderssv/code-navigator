package no.f12.codenavigator.gradle

import no.f12.codenavigator.config.OutputFormat

import no.f12.codenavigator.formatting.OutputWrapper
import no.f12.codenavigator.registry.TaskRegistry
import no.f12.codenavigator.navigation.dsm.PackageDependencyFormatter
import no.f12.codenavigator.navigation.dsm.PackageDepsConfig
import no.f12.codenavigator.navigation.dsm.PackageDepsOrchestrator

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

        val buildDir = project.layout.buildDirectory.asFile.get()
        val output = PackageDepsOrchestrator.run(
            config,
            project.taggedClassDirectories(),
            File(buildDir, "cnav"),
            File(buildDir, "cnav/skipped-files.txt"),
        )
        output.skippedFileWarning?.let { logger.warn(it) }

        if (output.emptyMessage != null) {
            logger.quiet(OutputWrapper.emptyResult(config.format, output.emptyMessage, output.emptyHints))
            return
        }

        logger.quiet(
            OutputWrapper.formatAndWrap(config.format) { format ->
                when (format) {
                    OutputFormat.TEXT, OutputFormat.DIFF -> PackageDependencyFormatter.format(output.deps, output.packages, config.reverse)
                    OutputFormat.JSON -> PackageDependencyFormatter.formatJson(output.deps, output.packages, config.reverse)
                    OutputFormat.LLM -> PackageDependencyFormatter.formatLlm(output.deps, output.packages, config.reverse)
                }
            },
        )
    }
}
