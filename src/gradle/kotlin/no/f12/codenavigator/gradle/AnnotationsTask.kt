package no.f12.codenavigator.gradle

import no.f12.codenavigator.JsonFormatter
import no.f12.codenavigator.LlmFormatter
import no.f12.codenavigator.config.OutputFormat
import no.f12.codenavigator.OutputWrapper
import no.f12.codenavigator.navigation.AnnotationQueryBuilder
import no.f12.codenavigator.navigation.AnnotationQueryConfig
import no.f12.codenavigator.navigation.AnnotationQueryFormatter

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "Produces console output only")
abstract class AnnotationsTask : DefaultTask() {

    @TaskAction
    fun annotations() {
        val config = try {
            AnnotationQueryConfig.parse(
                project.buildPropertyMap(
                    propertyNames = listOf("pattern", "methods", "format", "llm"),
                    flagNames = emptyList(),
                ),
            )
        } catch (e: IllegalArgumentException) {
            throw GradleException(
                "Missing required property. Usage: ./gradlew cnavAnnotations -Ppattern=<regex> [-Pmethods=true]",
            )
        }

        val sourceSets = project.extensions.getByType(SourceSetContainer::class.java)
        val mainSourceSet = sourceSets.getByName("main")
        val classDirectories = mainSourceSet.output.classesDirs.files.toList()

        val matches = AnnotationQueryBuilder.query(classDirectories, config.pattern, config.methods)

        val output = when (config.format) {
            OutputFormat.JSON -> JsonFormatter.formatAnnotations(matches)
            OutputFormat.LLM -> LlmFormatter.formatAnnotations(matches)
            OutputFormat.TEXT -> AnnotationQueryFormatter.format(matches)
        }
        logger.lifecycle(OutputWrapper.wrap(output, config.format))
    }
}
