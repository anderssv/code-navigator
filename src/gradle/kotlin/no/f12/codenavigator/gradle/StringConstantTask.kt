package no.f12.codenavigator.gradle

import no.f12.codenavigator.JsonFormatter
import no.f12.codenavigator.LlmFormatter
import no.f12.codenavigator.TaskRegistry
import no.f12.codenavigator.config.OutputFormat
import no.f12.codenavigator.OutputWrapper
import no.f12.codenavigator.navigation.SkippedFileReporter
import no.f12.codenavigator.navigation.StringConstantConfig
import no.f12.codenavigator.navigation.StringConstantFormatter
import no.f12.codenavigator.navigation.StringConstantScanner

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.File

@DisableCachingByDefault(because = "Produces console output only")
abstract class StringConstantTask : DefaultTask() {

    @TaskAction
    fun findStringConstants() {
        val config = StringConstantConfig.parse(
            project.buildPropertyMap(TaskRegistry.FIND_STRING_CONSTANT),
        )

        val sourceSets = project.extensions.getByType(SourceSetContainer::class.java)
        val mainSourceSet = sourceSets.getByName("main")
        val classDirectories = mainSourceSet.output.classesDirs.files.toList()

        val result = StringConstantScanner.scan(classDirectories, config.pattern)
        val reportFile = File(project.layout.buildDirectory.asFile.get(), "cnav/skipped-files.txt")
        SkippedFileReporter.report(result.skippedFiles, reportFile)?.let { logger.warn(it) }
        val matches = result.data

        if (matches.isEmpty()) {
            logger.lifecycle("No string constants matching '${config.pattern.pattern}' found.")
            return
        }

        val output = when (config.format) {
            OutputFormat.JSON -> JsonFormatter.formatStringConstants(matches)
            OutputFormat.LLM -> LlmFormatter.formatStringConstants(matches)
            OutputFormat.TEXT -> StringConstantFormatter.format(matches)
        }
        logger.lifecycle(OutputWrapper.wrap(output, config.format))
    }
}
