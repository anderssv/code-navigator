package no.f12.codenavigator.gradle

import no.f12.codenavigator.config.OutputFormat

import no.f12.codenavigator.registry.BuildTool
import no.f12.codenavigator.formatting.OutputWrapper
import no.f12.codenavigator.registry.TaskRegistry
import no.f12.codenavigator.navigation.types.Scope
import no.f12.codenavigator.navigation.bytecode.SourceSetResolver
import no.f12.codenavigator.navigation.annotation.AnnotationQueryBuilder
import no.f12.codenavigator.navigation.annotation.AnnotationQueryConfig
import no.f12.codenavigator.navigation.annotation.AnnotationQueryFormatter

import org.gradle.api.GradleException
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "Produces console output only")
abstract class AnnotationsTask : CodeNavigatorTask() {

    @Option(option = "pattern", description = "Class/symbol name regex (camelCase-aware: MyService matches com.example.MyService)")
    @get:Internal
    var pattern: String? = null

    @Option(option = "methods", description = "Deprecated: class, method, and field annotations are all searched by default now")
    @get:Internal
    var methods: String? = null

    @Option(option = "target", description = "Annotation targets to search: class, method, field (default: all)")
    @get:Internal
    var target: String? = null

    @Option(option = "scope", description = "Filter by source set: all (default), prod (production only), test (test only)")
    @get:Internal
    var scope: String? = null

    @Option(option = "include-test", description = "Deprecated: test sources are now included by default. Use scope=prod to see only production code.")
    @get:Internal
    var includeTest: String? = null

    override fun taskOptionsMap(): Map<String, String?> = buildMap {
        pattern?.let { put("pattern", it) }
        methods?.let { put("methods", it) }
        target?.let { put("target", it) }
        scope?.let { put("scope", it) }
        includeTest?.let { put("include-test", it) }
    }

    @TaskAction
    fun annotations() {
        val properties = TaskRegistry.ANNOTATIONS.enhanceProperties(buildOptionsMap())
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

        val allMatches = AnnotationQueryBuilder.query(resolver.classDirectories, config.pattern, config.targets)
        val matches = allMatches.filter { resolver.sourceSetOf(it.className)?.let { ss -> config.scope.matchesSourceSet(ss) } ?: true }

        if (matches.isEmpty()) {
            logger.quiet(OutputWrapper.emptyResult(config.format, "No annotations matching '${config.pattern}' found.", AnnotationQueryFormatter.noResultsHints(config.pattern, config.targets)))
            return
        }

        logger.quiet(OutputWrapper.formatAndWrap(config.format) { format ->
    when (format) {
        OutputFormat.TEXT, OutputFormat.DIFF -> AnnotationQueryFormatter.format(matches)
        OutputFormat.JSON -> AnnotationQueryFormatter.formatJson(matches)
        OutputFormat.LLM -> AnnotationQueryFormatter.formatLlm(matches)
    }
})
    }
}
