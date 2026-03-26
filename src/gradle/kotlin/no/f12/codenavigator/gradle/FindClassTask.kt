package no.f12.codenavigator.gradle

import no.f12.codenavigator.JsonFormatter
import no.f12.codenavigator.LlmFormatter
import no.f12.codenavigator.OutputFormat
import no.f12.codenavigator.OutputWrapper
import no.f12.codenavigator.TableFormatter
import no.f12.codenavigator.navigation.ClassFilter
import no.f12.codenavigator.navigation.ClassIndexCache
import no.f12.codenavigator.navigation.FindClassConfig
import no.f12.codenavigator.navigation.SkippedFileReporter

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.File

@DisableCachingByDefault(because = "Produces console output only")
abstract class FindClassTask : DefaultTask() {

    @TaskAction
    fun findClass() {
        val config = try {
            FindClassConfig.parse(
                project.buildPropertyMap(
                    propertyNames = listOf("pattern", "format", "llm"),
                    flagNames = emptyList(),
                ),
            )
        } catch (e: IllegalArgumentException) {
            throw GradleException(
                "Missing required property 'pattern'. Usage: ./gradlew cnavFindClass -Ppattern=<regex>",
            )
        }

        val sourceSets = project.extensions.getByType(SourceSetContainer::class.java)
        val mainSourceSet = sourceSets.getByName("main")
        val classDirectories = mainSourceSet.output.classesDirs.files.toList()
        val cacheFile = File(project.layout.buildDirectory.asFile.get(), "cnav/class-index.cache")

        val result = ClassIndexCache.getOrBuild(cacheFile, classDirectories)
        val reportFile = File(project.layout.buildDirectory.asFile.get(), "cnav/skipped-files.txt")
        SkippedFileReporter.report(result.skippedFiles, reportFile)?.let { logger.warn(it) }
        val allClasses = result.data
        val matches = ClassFilter.filter(allClasses, config.pattern)
        val output = when (config.format) {
            OutputFormat.JSON -> JsonFormatter.formatClasses(matches)
            OutputFormat.LLM -> LlmFormatter.formatClasses(matches)
            OutputFormat.TEXT -> TableFormatter.format(matches)
        }

        logger.lifecycle(OutputWrapper.wrap(output, config.format))
    }
}
