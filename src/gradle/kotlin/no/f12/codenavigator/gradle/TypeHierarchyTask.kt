package no.f12.codenavigator.gradle

import no.f12.codenavigator.JsonFormatter
import no.f12.codenavigator.LlmFormatter
import no.f12.codenavigator.OutputWrapper
import no.f12.codenavigator.TaskRegistry
import no.f12.codenavigator.navigation.SkippedFileReporter
import no.f12.codenavigator.navigation.hierarchy.TypeHierarchyBuilder
import no.f12.codenavigator.navigation.hierarchy.TypeHierarchyConfig
import no.f12.codenavigator.navigation.hierarchy.TypeHierarchyFormatter

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.File

@DisableCachingByDefault(because = "Produces console output only")
abstract class TypeHierarchyTask : DefaultTask() {

    @TaskAction
    fun showTypeHierarchy() {
        val config = try {
            TypeHierarchyConfig.parse(
                project.buildPropertyMap(TaskRegistry.TYPE_HIERARCHY),
            )
        } catch (e: IllegalArgumentException) {
            throw GradleException(
                "Missing required property 'pattern'. Usage: ./gradlew cnavTypeHierarchy -Ppattern=<regex>",
            )
        }

        val sourceSets = project.extensions.getByType(SourceSetContainer::class.java)
        val classDirectories = sourceSets.getByName("main").output.classesDirs.files.toList()

        val results = TypeHierarchyBuilder.build(
            classDirectories,
            config.pattern,
            config.projectOnly,
        )

        if (results.isEmpty()) {
            logger.lifecycle("No classes found matching '${config.pattern}'")
            return
        }

        logger.lifecycle(OutputWrapper.formatAndWrap(config.format,
            text = { TypeHierarchyFormatter.format(results) },
            json = { JsonFormatter.formatTypeHierarchy(results) },
            llm = { LlmFormatter.formatTypeHierarchy(results) },
        ))
    }
}
