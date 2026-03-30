package no.f12.codenavigator.gradle

import no.f12.codenavigator.JsonFormatter
import no.f12.codenavigator.LlmFormatter
import no.f12.codenavigator.OutputWrapper
import no.f12.codenavigator.TaskRegistry
import no.f12.codenavigator.navigation.annotation.AnnotationQueryBuilder
import no.f12.codenavigator.navigation.annotation.AnnotationQueryConfig
import no.f12.codenavigator.navigation.annotation.AnnotationQueryFormatter

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
                project.buildPropertyMap(TaskRegistry.ANNOTATIONS),
            )
        } catch (e: IllegalArgumentException) {
            throw GradleException(
                "Missing required property. Usage: ./gradlew cnavAnnotations -Ppattern=<regex> [-Pmethods=true]",
            )
        }

        val sourceSets = project.extensions.getByType(SourceSetContainer::class.java)
        val classDirectories = mutableListOf<java.io.File>()
        classDirectories.addAll(sourceSets.getByName("main").output.classesDirs.files)

        if (config.includeTest) {
            sourceSets.findByName("test")?.let { testSourceSet ->
                classDirectories.addAll(testSourceSet.output.classesDirs.files)
            }
        }

        val matches = AnnotationQueryBuilder.query(classDirectories, config.pattern, config.methods)

        logger.lifecycle(OutputWrapper.formatAndWrap(config.format,
            text = { AnnotationQueryFormatter.format(matches) },
            json = { JsonFormatter.formatAnnotations(matches) },
            llm = { LlmFormatter.formatAnnotations(matches) },
        ))
    }
}
