package no.f12.codenavigator.gradle

import no.f12.codenavigator.formatting.DsmOutputFormatter
import no.f12.codenavigator.registry.TaskRegistry
import no.f12.codenavigator.navigation.dsm.MoveSuggestConfig
import no.f12.codenavigator.navigation.dsm.MoveSuggestOrchestrator
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.File

@DisableCachingByDefault(because = "Produces console output only")
abstract class MoveSuggestTask : DefaultTask() {

    @TaskAction
    fun showMoveSuggestions() {
        val extension = project.codeNavigatorExtension()
        val cliProps = project.buildPropertyMap(TaskRegistry.MOVE_SUGGEST)
        val props = extension.resolveProperties(cliProps)

        val config = MoveSuggestConfig.parse(props)

        val taggedDirs = project.taggedClassDirectories()
        val filteredDirs = taggedDirs.filter { config.scope.matchesSourceSet(it.second) }
        val classDirectories = filteredDirs.map { it.first }

        val reportFile = File(project.layout.buildDirectory.asFile.get(), "cnav/skipped-files.txt")
        val output = MoveSuggestOrchestrator.run(config, classDirectories, reportFile)

        output.skippedFileWarning?.let { logger.warn(it) }
        DsmOutputFormatter.format(output, config.format)?.let { logger.lifecycle(it) }
    }
}
