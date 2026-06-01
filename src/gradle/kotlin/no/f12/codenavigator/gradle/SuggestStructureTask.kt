package no.f12.codenavigator.gradle

import no.f12.codenavigator.formatting.DsmOutputFormatter
import no.f12.codenavigator.navigation.dsm.SuggestStructureConfig
import no.f12.codenavigator.navigation.dsm.SuggestStructureOrchestrator
import no.f12.codenavigator.registry.TaskRegistry
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import org.gradle.work.DisableCachingByDefault
import java.io.File

@DisableCachingByDefault(because = "Produces console output only")
abstract class SuggestStructureTask : CodeNavigatorTask() {

    @Option(option = "package-filter", description = "Only include packages under this prefix")
    @get:Internal
    var packageFilter: String? = null

    @Option(option = "top", description = "Max results")
    @get:Internal
    var top: String? = null

    @Option(option = "max-fan-in", description = "Exclude ubiquitous types with fan-in above this threshold from move suggestions")
    @get:Internal
    var maxFanIn: String? = null

    @Option(option = "min-group-size", description = "Minimum number of classes in a structure group")
    @get:Internal
    var minGroupSize: String? = null

    @Option(option = "scope", description = "Filter by source set: all (default), prod (production only), test (test only)")
    @get:Internal
    var scope: String? = null

    override fun taskOptionsMap(): Map<String, String?> = buildMap {
        packageFilter?.let { put("package-filter", it) }
        top?.let { put("top", it) }
        maxFanIn?.let { put("max-fan-in", it) }
        minGroupSize?.let { put("min-group-size", it) }
        scope?.let { put("scope", it) }
    }

    @TaskAction
    fun suggestStructure() {
        val extension = project.codeNavigatorExtension()
        val props = extension.resolveProperties(TaskRegistry.SUGGEST_STRUCTURE.enhanceProperties(buildOptionsMap()))

        val config = SuggestStructureConfig.parse(props)

        val taggedDirs = project.taggedClassDirectories()
        val filteredDirs = taggedDirs.filter { config.scope.matchesSourceSet(it.second) }
        val classDirectories = filteredDirs.map { it.first }

        val reportFile = File(project.layout.buildDirectory.asFile.get(), "cnav/skipped-files.txt")
        val output = SuggestStructureOrchestrator.run(config, classDirectories, reportFile)

        output.skippedFileWarning?.let { logger.warn(it) }
        DsmOutputFormatter.format(output, config.format)?.let { logger.lifecycle(it) }
    }
}
