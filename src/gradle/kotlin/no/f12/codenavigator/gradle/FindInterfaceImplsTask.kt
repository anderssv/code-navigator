package no.f12.codenavigator.gradle

import no.f12.codenavigator.registry.BuildTool
import no.f12.codenavigator.JsonFormatter
import no.f12.codenavigator.LlmFormatter
import no.f12.codenavigator.OutputWrapper
import no.f12.codenavigator.registry.TaskRegistry
import no.f12.codenavigator.navigation.SourceSet
import no.f12.codenavigator.navigation.SourceSetResolver
import no.f12.codenavigator.navigation.interfaces.FindInterfaceImplsConfig
import no.f12.codenavigator.navigation.interfaces.InterfaceFormatter
import no.f12.codenavigator.navigation.interfaces.InterfaceRegistryCache
import no.f12.codenavigator.navigation.SkippedFileReporter

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.File

@DisableCachingByDefault(because = "Produces console output only")
abstract class FindInterfaceImplsTask : DefaultTask() {

    @TaskAction
    fun findImplementors() {
        val properties = project.buildPropertyMap(TaskRegistry.FIND_INTERFACES)
        TaskRegistry.FIND_INTERFACES.deprecations(properties).forEach { logger.warn(it) }
        val config = try {
            FindInterfaceImplsConfig.parse(properties)
        } catch (e: IllegalArgumentException) {
            throw GradleException(
                "${e.message}\n${TaskRegistry.FIND_INTERFACES.usageHint(BuildTool.GRADLE)}",
            )
        }

        val taggedDirs = project.taggedClassDirectories()
        val resolver = SourceSetResolver.from(taggedDirs)

        val cacheFile = File(project.layout.buildDirectory.asFile.get(), "cnav/interface-registry-all.cache")
        val result = InterfaceRegistryCache.getOrBuild(cacheFile, resolver.classDirectories)
        val reportFile = File(project.layout.buildDirectory.asFile.get(), "cnav/skipped-files.txt")
        SkippedFileReporter.report(result.skippedFiles, reportFile)?.let { logger.warn(it) }

        val registry = when {
            config.prodOnly -> result.data.filteredByImplementor { resolver.sourceSetOf(it) == SourceSet.MAIN }
            config.testOnly -> result.data.filteredByImplementor { resolver.sourceSetOf(it) == SourceSet.TEST }
            else -> result.data
        }
        val matchingInterfaces = registry.findInterfaces(config.pattern)

        if (matchingInterfaces.isEmpty()) {
            logger.lifecycle(OutputWrapper.emptyResult(config.format, "No interfaces found matching '${config.pattern}'"))
            return
        }

        logger.lifecycle(OutputWrapper.formatAndWrap(config.format,
            text = { InterfaceFormatter.format(registry, matchingInterfaces) },
            json = { JsonFormatter.formatInterfaces(registry, matchingInterfaces) },
            llm = { LlmFormatter.formatInterfaces(registry, matchingInterfaces) },
        ))
    }
}
