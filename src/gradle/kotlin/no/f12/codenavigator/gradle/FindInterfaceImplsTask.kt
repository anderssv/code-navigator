package no.f12.codenavigator.gradle

import no.f12.codenavigator.config.OutputFormat

import no.f12.codenavigator.registry.BuildTool
import no.f12.codenavigator.formatting.OutputWrapper
import no.f12.codenavigator.registry.TaskRegistry
import no.f12.codenavigator.navigation.types.Scope
import no.f12.codenavigator.navigation.bytecode.SourceSetResolver
import no.f12.codenavigator.navigation.relations.implementors.FindInterfaceImplsConfig
import no.f12.codenavigator.navigation.relations.implementors.InterfaceFormatter
import no.f12.codenavigator.navigation.relations.implementors.InterfaceRegistryCache
import no.f12.codenavigator.navigation.bytecode.SkippedFileReporter

import org.gradle.api.GradleException
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import org.gradle.work.DisableCachingByDefault
import java.io.File

@DisableCachingByDefault(because = "Produces console output only")
abstract class FindInterfaceImplsTask : CodeNavigatorTask() {

    @Option(option = "pattern", description = "Class/symbol name regex (camelCase-aware: MyService matches com.example.MyService)")
    @get:Internal
    var pattern: String? = null

    @Option(option = "scope", description = "Filter by source set: all (default), prod (production only), test (test only)")
    @get:Internal
    var scope: String? = null

    @Option(option = "include-test", description = "Deprecated: test sources are now included by default. Use scope=prod to see only production code.")
    @get:Internal
    var includeTest: String? = null

    override fun taskOptionsMap(): Map<String, String?> = buildMap {
        pattern?.let { put("pattern", it) }
        scope?.let { put("scope", it) }
        includeTest?.let { put("include-test", it) }
    }

    @TaskAction
    fun findImplementors() {
        val properties = TaskRegistry.FIND_INTERFACES.enhanceProperties(buildOptionsMap())
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

        val registry = if (config.scope == Scope.ALL) result.data
            else result.data.filteredByImplementor { resolver.sourceSetOf(it)?.let { ss -> config.scope.matchesSourceSet(ss) } ?: true }
        val matchingInterfaces = registry.findInterfaces(config.pattern)

        if (matchingInterfaces.isEmpty()) {
            logger.lifecycle(OutputWrapper.emptyResult(config.format, "No interfaces found matching '${config.pattern}'"))
            return
        }

        logger.lifecycle(OutputWrapper.formatAndWrap(config.format) { format ->
    when (format) {
        OutputFormat.TEXT, OutputFormat.DIFF -> InterfaceFormatter.format(registry, matchingInterfaces)
        OutputFormat.JSON -> InterfaceFormatter.formatJson(registry, matchingInterfaces)
        OutputFormat.LLM -> InterfaceFormatter.formatLlm(registry, matchingInterfaces)
    }
})
    }
}
