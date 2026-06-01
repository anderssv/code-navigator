package no.f12.codenavigator.gradle

import no.f12.codenavigator.config.OutputFormat

import no.f12.codenavigator.registry.BuildTool
import no.f12.codenavigator.formatting.JsonFormatter
import no.f12.codenavigator.formatting.LlmFormatter
import no.f12.codenavigator.formatting.OutputWrapper
import no.f12.codenavigator.registry.TaskDef
import no.f12.codenavigator.navigation.bytecode.SkippedFileReporter
import no.f12.codenavigator.navigation.types.SourceSet
import no.f12.codenavigator.navigation.annotation.AnnotationExtractor
import no.f12.codenavigator.navigation.relations.callgraph.CallDirection
import no.f12.codenavigator.navigation.relations.callgraph.CallGraphCache
import no.f12.codenavigator.navigation.relations.callgraph.CallGraphConfig
import no.f12.codenavigator.navigation.relations.callgraph.CallTreeBuilder
import no.f12.codenavigator.navigation.relations.callgraph.CallTreeFormatter
import no.f12.codenavigator.navigation.relations.implementors.InterfaceRegistryCache
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.logging.Logger
import org.gradle.api.tasks.SourceSetContainer
import java.io.File

object CallTreeTaskSupport {

    fun execute(
        project: Project,
        logger: Logger,
        taskDef: TaskDef,
        direction: CallDirection,
        properties: Map<String, String?>,
    ) {
        taskDef.deprecations(properties).forEach { logger.warn(it) }
        val config = try {
            CallGraphConfig.parse(properties)
        } catch (e: IllegalArgumentException) {
            throw GradleException("${e.message}\n${taskDef.usageHint(BuildTool.GRADLE)}")
        }

        val taggedDirs = project.taggedClassDirectories()
        val classDirectories = taggedDirs.map { it.first }

        val cacheFile = File(project.layout.buildDirectory.asFile.get(), "cnav/call-graph.cache")
        val result = CallGraphCache.getOrBuildTagged(cacheFile, taggedDirs)
        val reportFile = File(project.layout.buildDirectory.asFile.get(), "cnav/skipped-files.txt")
        SkippedFileReporter.report(result.skippedFiles, reportFile)?.let { logger.warn(it) }
        val graph = result.data
        val methods = graph.findMethods(config.method)

        if (methods.isEmpty()) {
            logger.lifecycle(OutputWrapper.emptyResult(config.format, "No methods found matching '${config.method}'"))
            return
        }

        val interfaceRegistry = InterfaceRegistryCache.getOrBuild(
            File(project.layout.buildDirectory.asFile.get(), "cnav/interface-registry.cache"),
            classDirectories,
        ).data
        val interfaceImplementors = interfaceRegistry.implementorMap()
        val classToInterfaces = interfaceRegistry.classToInterfacesMap()

        val annotations = AnnotationExtractor.scanAll(classDirectories)

        val trees = CallTreeBuilder.build(
            graph, methods, config.maxDepth, direction, config.buildFilter(graph),
            interfaceImplementors = interfaceImplementors,
            classToInterfaces = classToInterfaces,
            classAnnotations = annotations.classAnnotations,
            methodAnnotations = annotations.methodAnnotations,
            classAnnotationParameters = annotations.classAnnotationParameters,
            methodAnnotationParameters = annotations.methodAnnotationParameters,
        )

        val classHint = CallTreeFormatter.classMatchHint(config.method, methods)

        logger.lifecycle(
            OutputWrapper.formatAndWrap(config.format) { format ->
                when (format) {
                    OutputFormat.TEXT, OutputFormat.DIFF -> CallTreeFormatter.renderTrees(trees, direction) + (classHint?.let { "\n\n$it" } ?: "")
                    OutputFormat.JSON -> JsonFormatter.renderCallTrees(trees, direction)
                    OutputFormat.LLM -> LlmFormatter.renderCallTrees(trees, direction) + (classHint?.let { "\n\n$it" } ?: "")
                }
            },
        )
    }
}
