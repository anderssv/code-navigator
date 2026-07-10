package no.f12.codenavigator.gradle

import no.f12.codenavigator.config.OutputFormat

import no.f12.codenavigator.registry.BuildTool
import no.f12.codenavigator.formatting.OutputWrapper
import no.f12.codenavigator.registry.TaskRegistry
import no.f12.codenavigator.navigation.types.Scope
import no.f12.codenavigator.navigation.bytecode.SourceSetResolver
import no.f12.codenavigator.navigation.bytecode.JarClassScanner
import no.f12.codenavigator.navigation.classinfo.ClassInfoExtractor
import no.f12.codenavigator.navigation.symbol.FindSymbolConfig
import no.f12.codenavigator.navigation.bytecode.SkippedFileReporter
import no.f12.codenavigator.navigation.symbol.SymbolExtractor
import no.f12.codenavigator.navigation.symbol.SymbolFilter
import no.f12.codenavigator.navigation.symbol.SymbolIndexCache
import no.f12.codenavigator.navigation.symbol.SymbolTableFormatter

import org.gradle.api.GradleException
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import org.gradle.work.DisableCachingByDefault
import java.io.File

@DisableCachingByDefault(because = "Produces console output only")
abstract class FindSymbolTask : CodeNavigatorTask() {

    @Option(option = "pattern", description = "Class/symbol name regex (camelCase-aware: MyService matches com.example.MyService)")
    @get:Internal
    var pattern: String? = null

    @Option(option = "jar", description = "Scan a JAR file instead of project classes. Value: file path or artifact coordinate (group:name)")
    @get:Internal
    var jar: String? = null

    @Option(option = "scope", description = "Filter by source set: all (default), prod (production only), test (test only)")
    @get:Internal
    var scope: String? = null

    @Option(option = "include-test", description = "Deprecated: test sources are now included by default. Use scope=prod to see only production code.")
    @get:Internal
    var includeTest: String? = null

    override fun taskOptionsMap(): Map<String, String?> = buildMap {
        pattern?.let { put("pattern", it) }
        jar?.let { put("jar", it) }
        scope?.let { put("scope", it) }
        includeTest?.let { put("include-test", it) }
    }

    @TaskAction
    fun findSymbol() {
        val properties = TaskRegistry.FIND_SYMBOL.enhanceProperties(buildOptionsMap())
        TaskRegistry.FIND_SYMBOL.deprecations(properties).forEach { logger.warn(it) }
        val config = try {
            FindSymbolConfig.parse(properties)
        } catch (e: IllegalArgumentException) {
            throw GradleException(
                "${e.message}\n${TaskRegistry.FIND_SYMBOL.usageHint(BuildTool.GRADLE)}",
            )
        }

        val allSymbols = if (config.jar != null) {
            val jarFile = project.resolveJar(config.jar)
            val entries = JarClassScanner.scan(jarFile)
            entries.flatMap { entry ->
                try {
                    val info = ClassInfoExtractor.extract(entry.bytes)
                    if (info.isUserDefinedClass) {
                        SymbolExtractor.extract(entry.bytes)
                    } else {
                        emptyList()
                    }
                } catch (_: Exception) {
                    emptyList()
                }
            }.sortedWith(compareBy({ it.packageName.toString() }, { it.className }, { it.symbolName }))
        } else {
            val taggedDirs = project.taggedClassDirectories()
            val resolver = SourceSetResolver.from(taggedDirs)

            val cacheFile = File(project.layout.buildDirectory.asFile.get(), "cnav/symbol-index-all.cache")
            val result = SymbolIndexCache.getOrBuild(cacheFile, resolver.classDirectories)
            val reportFile = File(project.layout.buildDirectory.asFile.get(), "cnav/skipped-files.txt")
            SkippedFileReporter.report(result.skippedFiles, reportFile)?.let { logger.warn(it) }
            result.data.filter { resolver.sourceSetOf(it.className)?.let { ss -> config.scope.matchesSourceSet(ss) } ?: true }
        }

        val matches = SymbolFilter.filter(allSymbols, config.pattern)
        if (matches.isEmpty()) {
            logger.lifecycle(OutputWrapper.emptyResult(config.format, "No symbols matching '${config.pattern}' found."))
            return
        }
        logger.lifecycle(OutputWrapper.formatAndWrap(config.format) { format ->
    when (format) {
        OutputFormat.TEXT, OutputFormat.DIFF -> SymbolTableFormatter.format(matches)
        OutputFormat.JSON -> SymbolTableFormatter.formatJson(matches)
        OutputFormat.LLM -> SymbolTableFormatter.formatLlm(matches)
    }
})
    }
}
