package no.f12.codenavigator.gradle

import no.f12.codenavigator.formatting.DsmOutputFormatter
import no.f12.codenavigator.navigation.dsm.BalanceConfig
import no.f12.codenavigator.navigation.dsm.BalanceOrchestrator
import no.f12.codenavigator.registry.TaskRegistry
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.File

@DisableCachingByDefault(because = "Produces console output only")
abstract class BalanceTask : DefaultTask() {

    @TaskAction
    fun showBalance() {
        val extension = project.codeNavigatorExtension()
        val cliProps = project.buildPropertyMap(TaskRegistry.BALANCE)
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
