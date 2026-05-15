package no.f12.codenavigator.gradle

import no.f12.codenavigator.registry.TaskRegistry
import no.f12.codenavigator.navigation.dsm.DistanceOrchestrator
import no.f12.codenavigator.navigation.dsm.PackageDistanceConfig
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.File

@DisableCachingByDefault(because = "Produces console output only")
abstract class PackageDistanceTask : DefaultTask() {

    @TaskAction
    fun showDistance() {
        val extension = project.codeNavigatorExtension()
        val cliProps = project.buildPropertyMap(TaskRegistry.DISTANCE)
        val props = extension.resolveProperties(cliProps)

        val config = PackageDistanceConfig.parse(props)

        val taggedDirs = project.taggedClassDirectories()
        val filteredDirs = taggedDirs.filter { config.scope.matchesSourceSet(it.second) }
        val classDirectories = filteredDirs.map { it.first }

        val reportFile = File(project.layout.buildDirectory.asFile.get(), "cnav/skipped-files.txt")
        val output = DistanceOrchestrator.run(config, classDirectories, reportFile)

        output.skippedFileWarning?.let { logger.warn(it) }
        output.formatted?.let { logger.lifecycle(it) }
    }
}
