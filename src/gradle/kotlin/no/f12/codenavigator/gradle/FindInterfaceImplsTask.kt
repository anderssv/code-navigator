package no.f12.codenavigator.gradle

import no.f12.codenavigator.BuildTool
import no.f12.codenavigator.JsonFormatter
import no.f12.codenavigator.LlmFormatter
import no.f12.codenavigator.OutputWrapper
import no.f12.codenavigator.TaskRegistry
import no.f12.codenavigator.navigation.interfaces.FindInterfaceImplsConfig
import no.f12.codenavigator.navigation.interfaces.InterfaceFormatter
import no.f12.codenavigator.navigation.interfaces.InterfaceRegistryCache
import no.f12.codenavigator.navigation.SkippedFileReporter

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.File

@DisableCachingByDefault(because = "Produces console output only")
abstract class FindInterfaceImplsTask : DefaultTask() {

    @TaskAction
    fun findImplementors() {
        val config = try {
            FindInterfaceImplsConfig.parse(
                project.buildPropertyMap(TaskRegistry.FIND_INTERFACES),
            )
        } catch (e: IllegalArgumentException) {
            throw GradleException(
                "${e.message}\n${TaskRegistry.FIND_INTERFACES.usageHint(BuildTool.GRADLE)}",
            )
        }

        val sourceSets = project.extensions.getByType(SourceSetContainer::class.java)
        val classDirectories = mutableListOf<File>()
        classDirectories.addAll(sourceSets.getByName("main").output.classesDirs.files)

        val cacheFileName = if (config.includeTest) {
            sourceSets.findByName("test")?.let { testSourceSet ->
                classDirectories.addAll(testSourceSet.output.classesDirs.files)
            }
            "interface-registry-all.cache"
        } else {
            "interface-registry.cache"
        }

        val cacheFile = File(project.layout.buildDirectory.asFile.get(), "cnav/$cacheFileName")
        val result = InterfaceRegistryCache.getOrBuild(cacheFile, classDirectories)
        val reportFile = File(project.layout.buildDirectory.asFile.get(), "cnav/skipped-files.txt")
        SkippedFileReporter.report(result.skippedFiles, reportFile)?.let { logger.warn(it) }
        val registry = result.data
        val matchingInterfaces = registry.findInterfaces(config.pattern)

        if (matchingInterfaces.isEmpty()) {
            logger.lifecycle("No interfaces found matching '${config.pattern}'")
            return
        }

        logger.lifecycle(OutputWrapper.formatAndWrap(config.format,
            text = { InterfaceFormatter.format(registry, matchingInterfaces) },
            json = { JsonFormatter.formatInterfaces(registry, matchingInterfaces) },
            llm = { LlmFormatter.formatInterfaces(registry, matchingInterfaces) },
        ))
    }
}
