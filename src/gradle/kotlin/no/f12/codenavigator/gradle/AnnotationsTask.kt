package no.f12.codenavigator.gradle

import no.f12.codenavigator.registry.BuildTool
import no.f12.codenavigator.formatting.JsonFormatter
import no.f12.codenavigator.formatting.LlmFormatter
import no.f12.codenavigator.formatting.OutputWrapper
import no.f12.codenavigator.registry.TaskRegistry
import no.f12.codenavigator.navigation.core.SourceSet
import no.f12.codenavigator.navigation.core.SourceSetResolver
import no.f12.codenavigator.navigation.annotation.AnnotationQueryBuilder
import no.f12.codenavigator.navigation.annotation.AnnotationQueryConfig
import no.f12.codenavigator.navigation.annotation.AnnotationQueryFormatter

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "Produces console output only")
abstract class AnnotationsTask : DefaultTask() {

    @TaskAction
    fun annotations() {
        val properties = project.buildPropertyMap(TaskRegistry.ANNOTATIONS)
        TaskRegistry.ANNOTATIONS.deprecations(properties).forEach { logger.warn(it) }
        val config = try {
            AnnotationQueryConfig.parse(properties)
        } catch (e: IllegalArgumentException) {
            throw GradleException(
                "${e.message}\n${TaskRegistry.ANNOTATIONS.usageHint(BuildTool.GRADLE)}",
            )
        }

        val taggedDirs = project.taggedClassDirectories()
        val resolver = SourceSetResolver.from(taggedDirs)

        val allMatches = AnnotationQueryBuilder.query(resolver.classDirectories, config.pattern, config.methods)
        val matches = when {
            config.prodOnly -> allMatches.filter { resolver.sourceSetOf(it.className) == SourceSet.MAIN }
            config.testOnly -> allMatches.filter { resolver.sourceSetOf(it.className) == SourceSet.TEST }
            else -> allMatches
        }

        if (matches.isEmpty()) {
            logger.lifecycle(OutputWrapper.emptyResult(config.format, "No annotations matching '${config.pattern}' found.", AnnotationQueryFormatter.noResultsHints(config.pattern, config.methods)))
            return
        }

        logger.lifecycle(OutputWrapper.formatAndWrap(config.format,
            text = { AnnotationQueryFormatter.format(matches) },
            json = { JsonFormatter.formatAnnotations(matches) },
            llm = { LlmFormatter.formatAnnotations(matches) },
        ))
    }
}
