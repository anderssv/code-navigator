package no.f12.codenavigator.gradle

import no.f12.codenavigator.config.OutputFormat

import no.f12.codenavigator.registry.BuildTool
import no.f12.codenavigator.formatting.OutputWrapper
import no.f12.codenavigator.registry.TaskRegistry
import no.f12.codenavigator.navigation.types.Scope
import no.f12.codenavigator.navigation.bytecode.SourceSetResolver
import no.f12.codenavigator.navigation.relations.hierarchy.TypeHierarchyBuilder
import no.f12.codenavigator.navigation.relations.hierarchy.TypeHierarchyConfig
import no.f12.codenavigator.navigation.relations.hierarchy.TypeHierarchyFormatter

import org.gradle.api.GradleException
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "Produces console output only")
abstract class TypeHierarchyTask : CodeNavigatorTask() {

    @Option(option = "pattern", description = "Class/symbol name regex (camelCase-aware: MyService matches com.example.MyService)")
    @get:Internal
    var pattern: String? = null

    @Option(option = "project-only", description = "Hide JDK/stdlib/library classes (default: on)")
    @get:Internal
    var projectOnly: String? = null

    @Option(option = "scope", description = "Filter by source set: all (default), prod (production only), test (test only)")
    @get:Internal
    var scope: String? = null

    override fun taskOptionsMap(): Map<String, String?> = buildMap {
        pattern?.let { put("pattern", it) }
        projectOnly?.let { put("project-only", it) }
        scope?.let { put("scope", it) }
    }

    @TaskAction
    fun showTypeHierarchy() {
        val config = try {
            TypeHierarchyConfig.parse(
                TaskRegistry.TYPE_HIERARCHY.enhanceProperties(buildOptionsMap()),
            )
        } catch (e: IllegalArgumentException) {
            throw GradleException(
                "${e.message}\n${TaskRegistry.TYPE_HIERARCHY.usageHint(BuildTool.GRADLE)}",
            )
        }

        val taggedDirs = project.taggedClassDirectories()
        val resolver = SourceSetResolver.from(taggedDirs)

        val classpath = if (!config.projectOnly) {
            project.configurations.findByName("runtimeClasspath")
                ?.resolve()
                ?.map { it.toPath() }
                ?: emptyList()
        } else {
            emptyList()
        }

        val allResults = TypeHierarchyBuilder.build(
            resolver.classDirectories,
            config.pattern,
            config.projectOnly,
            classpath,
        )
        val results = allResults.filter { resolver.sourceSetOf(it.className)?.let { ss -> config.scope.matchesSourceSet(ss) } ?: true }

        if (results.isEmpty()) {
            logger.lifecycle(OutputWrapper.emptyResult(config.format, "No classes found matching '${config.pattern}'"))
            return
        }

        logger.lifecycle(OutputWrapper.formatAndWrap(config.format) { format ->
    when (format) {
        OutputFormat.TEXT, OutputFormat.DIFF -> TypeHierarchyFormatter.format(results)
        OutputFormat.JSON -> TypeHierarchyFormatter.formatJson(results)
        OutputFormat.LLM -> TypeHierarchyFormatter.formatLlm(results)
    }
})
    }
}
