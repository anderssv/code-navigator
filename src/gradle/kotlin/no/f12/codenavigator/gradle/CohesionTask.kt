package no.f12.codenavigator.gradle

import no.f12.codenavigator.formatting.DsmOutputFormatter
import no.f12.codenavigator.registry.TaskRegistry
import no.f12.codenavigator.navigation.dsm.CohesionConfig
import no.f12.codenavigator.navigation.dsm.CohesionOrchestrator
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import org.gradle.work.DisableCachingByDefault
import java.io.File

@DisableCachingByDefault(because = "Produces console output only")
abstract class CohesionTask : CodeNavigatorTask() {

    @Option(option = "package-filter", description = "Only include packages under this prefix")
    @get:Internal
    var packageFilter: String? = null

    @Option(option = "top", description = "Max results")
    @get:Internal
    var top: String? = null

    @Option(option = "min-edges", description = "Minimum total edges (internal+external) to include a package")
    @get:Internal
    var minEdges: String? = null

    @Option(option = "scope", description = "Filter by source set: all (default), prod (production only), test (test only)")
    @get:Internal
    var scope: String? = null

    override fun taskOptionsMap(): Map<String, String?> = buildMap {
        packageFilter?.let { put("package-filter", it) }
        top?.let { put("top", it) }
        minEdges?.let { put("min-edges", it) }
        scope?.let { put("scope", it) }
    }

    @TaskAction
    fun showCohesion() {
        val extension = project.codeNavigatorExtension()
        val cliProps = TaskRegistry.COHESION.enhanceProperties(buildOptionsMap())
        val props = extension.resolveProperties(cliProps)

        val config = CohesionConfig.parse(props)

        val taggedDirs = project.taggedClassDirectories()
        val filteredDirs = taggedDirs.filter { config.scope.matchesSourceSet(it.second) }
        val classDirectories = filteredDirs.map { it.first }

        val reportFile = File(project.layout.buildDirectory.asFile.get(), "cnav/skipped-files.txt")
        val output = CohesionOrchestrator.run(config, classDirectories, reportFile)

        output.skippedFileWarning?.let { logger.warn(it) }
        DsmOutputFormatter.format(output, config.format)?.let { logger.quiet(it) }
    }
}
