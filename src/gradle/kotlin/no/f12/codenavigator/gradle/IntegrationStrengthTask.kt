package no.f12.codenavigator.gradle

import no.f12.codenavigator.formatting.DsmOutputFormatter
import no.f12.codenavigator.registry.TaskRegistry
import no.f12.codenavigator.navigation.dsm.StrengthConfig
import no.f12.codenavigator.navigation.dsm.StrengthOrchestrator

import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import org.gradle.work.DisableCachingByDefault
import java.io.File

@DisableCachingByDefault(because = "Produces console output only")
abstract class IntegrationStrengthTask : CodeNavigatorTask() {

    @Option(option = "package-filter", description = "Only include packages under this prefix")
    @get:Internal
    var packageFilter: String? = null

    @Option(option = "include-external", description = "Include dependencies on classes outside the project")
    @get:Internal
    var includeExternal: String? = null

    @Option(option = "dsm-depth", description = "Package grouping depth")
    @get:Internal
    var dsmDepth: String? = null

    @Option(option = "top", description = "Max results")
    @get:Internal
    var top: String? = null

    @Option(option = "scope", description = "Filter by source set: all (default), prod (production only), test (test only)")
    @get:Internal
    var scope: String? = null

    override fun taskOptionsMap(): Map<String, String?> = buildMap {
        packageFilter?.let { put("package-filter", it) }
        includeExternal?.let { put("include-external", it) }
        dsmDepth?.let { put("dsm-depth", it) }
        top?.let { put("top", it) }
        scope?.let { put("scope", it) }
    }

    @TaskAction
    fun showStrength() {
        val extension = project.codeNavigatorExtension()
        val props = extension.resolveProperties(TaskRegistry.STRENGTH.enhanceProperties(buildOptionsMap()))

        val config = StrengthConfig.parse(props)

        val taggedDirs = project.taggedClassDirectories()
        val filteredDirs = taggedDirs.filter { config.scope.matchesSourceSet(it.second) }
        val classDirectories = filteredDirs.map { it.first }

        val classpath = if (config.includeExternal) {
            project.configurations.findByName("runtimeClasspath")
                ?.resolve()
                ?.map { it.toPath() }
                ?: emptyList()
        } else {
            emptyList()
        }

        val reportFile = File(project.layout.buildDirectory.asFile.get(), "cnav/skipped-files.txt")
        val output = StrengthOrchestrator.run(config, classDirectories, reportFile, classpath)

        output.skippedFileWarning?.let { logger.warn(it) }
        DsmOutputFormatter.format(output, config.format)?.let { logger.quiet(it) }
    }
}
