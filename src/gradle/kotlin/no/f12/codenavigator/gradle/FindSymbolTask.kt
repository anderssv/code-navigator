package no.f12.codenavigator.gradle

import no.f12.codenavigator.JsonFormatter
import no.f12.codenavigator.LlmFormatter
import no.f12.codenavigator.OutputFormat
import no.f12.codenavigator.OutputWrapper
import no.f12.codenavigator.navigation.FindSymbolConfig
import no.f12.codenavigator.navigation.SkippedFileReporter
import no.f12.codenavigator.navigation.SymbolFilter
import no.f12.codenavigator.navigation.SymbolIndexCache
import no.f12.codenavigator.navigation.SymbolTableFormatter

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
                project.buildPropertyMap(
                    propertyNames = listOf("pattern", "format", "llm"),
                    flagNames = emptyList(),
                ),
            )
        } catch (e: IllegalArgumentException) {
            throw GradleException(
                "Missing required property 'pattern'. Usage: ./gradlew cnavFindSymbol -Ppattern=<regex>",
            )
        }

        val sourceSets = project.extensions.getByType(SourceSetContainer::class.java)
        val mainSourceSet = sourceSets.getByName("main")
        val classDirectories = mainSourceSet.output.classesDirs.files.toList()

        val cacheFile = File(project.layout.buildDirectory.asFile.get(), "cnav/symbol-index.cache")
        val result = SymbolIndexCache.getOrBuild(cacheFile, classDirectories)
        val reportFile = File(project.layout.buildDirectory.asFile.get(), "cnav/skipped-files.txt")
        SkippedFileReporter.report(result.skippedFiles, reportFile)?.let { logger.warn(it) }
        val allSymbols = result.data
        val matches = SymbolFilter.filter(allSymbols, config.pattern)
        val output = when (config.format) {
            OutputFormat.JSON -> JsonFormatter.formatSymbols(matches)
            OutputFormat.LLM -> LlmFormatter.formatSymbols(matches)
            OutputFormat.TEXT -> SymbolTableFormatter.format(matches)
        }

        logger.lifecycle(OutputWrapper.wrap(output, config.format))
    }
}
