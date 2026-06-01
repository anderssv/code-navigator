package no.f12.codenavigator.gradle

import no.f12.codenavigator.formatting.DsmOutputFormatter
import no.f12.codenavigator.navigation.dsm.BalanceConfig
import no.f12.codenavigator.navigation.dsm.BalanceOrchestrator
import no.f12.codenavigator.registry.TaskRegistry
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import org.gradle.work.DisableCachingByDefault
import java.io.File

@DisableCachingByDefault(because = "Produces console output only")
abstract class BalanceTask : CodeNavigatorTask() {

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

    @Option(option = "after", description = "Only consider commits after this date")
    @get:Internal
    var after: String? = null

    @Option(option = "min-revs", description = "Min revisions to include")
    @get:Internal
    var minRevs: String? = null

    @Option(option = "no-follow", description = "Disable git rename tracking")
    @get:Internal
    var noFollow: Boolean = false

    @Option(option = "scope", description = "Filter by source set: all (default), prod (production only), test (test only)")
    @get:Internal
    var scope: String? = null

    override fun taskOptionsMap(): Map<String, String?> = buildMap {
        packageFilter?.let { put("package-filter", it) }
        includeExternal?.let { put("include-external", it) }
        dsmDepth?.let { put("dsm-depth", it) }
        top?.let { put("top", it) }
        after?.let { put("after", it) }
        minRevs?.let { put("min-revs", it) }
        if (noFollow) put("no-follow", "true")
        scope?.let { put("scope", it) }
    }

    @TaskAction
    fun showBalance() {
        val extension = project.codeNavigatorExtension()
        val cliProps = TaskRegistry.BALANCE.enhanceProperties(buildOptionsMap())
        val props = extension.resolveProperties(cliProps)

        val config = BalanceConfig.parse(props)

        val taggedDirs = project.taggedClassDirectories()
        val filteredDirs = taggedDirs.filter { config.scope.matchesSourceSet(it.second) }
        val classDirectories = filteredDirs.map { it.first }

        val reportFile = File(project.layout.buildDirectory.asFile.get(), "cnav/skipped-files.txt")
        val output = BalanceOrchestrator.run(config, classDirectories, reportFile, project.projectDir)

        output.skippedFileWarning?.let { logger.warn(it) }
        DsmOutputFormatter.format(output, config.format)?.let { logger.lifecycle(it) }
    }
}
