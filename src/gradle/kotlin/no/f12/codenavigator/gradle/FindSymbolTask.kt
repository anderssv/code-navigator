package no.f12.codenavigator.gradle

import no.f12.codenavigator.BuildTool
import no.f12.codenavigator.JsonFormatter
import no.f12.codenavigator.LlmFormatter
import no.f12.codenavigator.OutputWrapper
import no.f12.codenavigator.TaskRegistry
import no.f12.codenavigator.navigation.symbol.FindSymbolConfig
import no.f12.codenavigator.navigation.SkippedFileReporter
import no.f12.codenavigator.navigation.symbol.SymbolFilter
import no.f12.codenavigator.navigation.symbol.SymbolIndexCache
import no.f12.codenavigator.navigation.symbol.SymbolTableFormatter

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.File

@DisableCachingByDefault(because = "Produces console output only")
abstract class FindSymbolTask : DefaultTask() {

    @TaskAction
    fun findSymbol() {
        val config = try {
            FindSymbolConfig.parse(
                project.buildPropertyMap(TaskRegistry.FIND_SYMBOL),
            )
        } catch (e: IllegalArgumentException) {
            throw GradleException(
                "${e.message}\n${TaskRegistry.FIND_SYMBOL.usageHint(BuildTool.GRADLE)}",
            )
        }

        val sourceSets = project.extensions.getByType(SourceSetContainer::class.java)
        val classDirectories = mutableListOf<File>()
        classDirectories.addAll(sourceSets.getByName("main").output.classesDirs.files)

        val cacheFileName = if (config.includeTest) {
            sourceSets.findByName("test")?.let { testSourceSet ->
                classDirectories.addAll(testSourceSet.output.classesDirs.files)
            }
            "symbol-index-all.cache"
        } else {
            "symbol-index.cache"
        }

        val cacheFile = File(project.layout.buildDirectory.asFile.get(), "cnav/$cacheFileName")
        val result = SymbolIndexCache.getOrBuild(cacheFile, classDirectories)
        val reportFile = File(project.layout.buildDirectory.asFile.get(), "cnav/skipped-files.txt")
        SkippedFileReporter.report(result.skippedFiles, reportFile)?.let { logger.warn(it) }
        val allSymbols = result.data
        val matches = SymbolFilter.filter(allSymbols, config.pattern)
        logger.lifecycle(OutputWrapper.formatAndWrap(config.format,
            text = { SymbolTableFormatter.format(matches) },
            json = { JsonFormatter.formatSymbols(matches) },
            llm = { LlmFormatter.formatSymbols(matches) },
        ))
    }
}
