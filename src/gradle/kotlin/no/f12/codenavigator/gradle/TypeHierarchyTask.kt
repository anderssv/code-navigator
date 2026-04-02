package no.f12.codenavigator.gradle

import no.f12.codenavigator.registry.BuildTool
import no.f12.codenavigator.formatting.JsonFormatter
import no.f12.codenavigator.formatting.LlmFormatter
import no.f12.codenavigator.formatting.OutputWrapper
import no.f12.codenavigator.registry.TaskRegistry
import no.f12.codenavigator.navigation.core.SourceSet
import no.f12.codenavigator.navigation.core.SourceSetResolver
import no.f12.codenavigator.navigation.hierarchy.TypeHierarchyBuilder
import no.f12.codenavigator.navigation.hierarchy.TypeHierarchyConfig
import no.f12.codenavigator.navigation.hierarchy.TypeHierarchyFormatter

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

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
                "${e.message}\n${TaskRegistry.TYPE_HIERARCHY.usageHint(BuildTool.GRADLE)}",
            )
        }

        val taggedDirs = project.taggedClassDirectories()
        val resolver = SourceSetResolver.from(taggedDirs)

        val allResults = TypeHierarchyBuilder.build(
            resolver.classDirectories,
            config.pattern,
            config.projectOnly,
        )
        val results = when {
            config.prodOnly -> allResults.filter { resolver.sourceSetOf(it.className) == SourceSet.MAIN }
            config.testOnly -> allResults.filter { resolver.sourceSetOf(it.className) == SourceSet.TEST }
            else -> allResults
        }

        if (results.isEmpty()) {
            logger.lifecycle(OutputWrapper.emptyResult(config.format, "No classes found matching '${config.pattern}'"))
            return
        }

        logger.lifecycle(OutputWrapper.formatAndWrap(config.format,
            text = { TypeHierarchyFormatter.format(results) },
            json = { JsonFormatter.formatTypeHierarchy(results) },
            llm = { LlmFormatter.formatTypeHierarchy(results) },
        ))
    }
}
