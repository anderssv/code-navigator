package no.f12.codenavigator.gradle

import no.f12.codenavigator.BuildTool
import no.f12.codenavigator.JsonFormatter
import no.f12.codenavigator.LlmFormatter
import no.f12.codenavigator.OutputWrapper
import no.f12.codenavigator.TaskRegistry
import no.f12.codenavigator.navigation.SkippedFileReporter
import no.f12.codenavigator.navigation.annotation.AnnotationExtractor
import no.f12.codenavigator.navigation.callgraph.CallDirection
import no.f12.codenavigator.navigation.callgraph.CallGraphCache
import no.f12.codenavigator.navigation.callgraph.CallTreeBuilder
import no.f12.codenavigator.navigation.callgraph.MethodRef
import no.f12.codenavigator.navigation.classinfo.ClassDetailScanner
import no.f12.codenavigator.navigation.context.ContextBuilder
import no.f12.codenavigator.navigation.context.ContextConfig
import no.f12.codenavigator.navigation.context.ContextFormatter
import no.f12.codenavigator.navigation.interfaces.InterfaceRegistryCache

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.File

@DisableCachingByDefault(because = "Produces console output only")
abstract class ContextTask : DefaultTask() {

    @TaskAction
    fun gatherContext() {
        val config = try {
            ContextConfig.parse(project.buildPropertyMap(TaskRegistry.CONTEXT))
        } catch (e: IllegalArgumentException) {
            throw GradleException(
                "${e.message}\n${TaskRegistry.CONTEXT.usageHint(BuildTool.GRADLE)}",
            )
        }

        val taggedDirs = project.taggedClassDirectories()
        val classDirectories = taggedDirs.map { it.first }

        val classResult = ClassDetailScanner.scan(classDirectories, config.pattern)
        val classReportFile = File(project.layout.buildDirectory.asFile.get(), "cnav/skipped-files.txt")
        SkippedFileReporter.report(classResult.skippedFiles, classReportFile)?.let { logger.warn(it) }
        val matchingDetails = classResult.data

        if (matchingDetails.isEmpty()) {
            logger.lifecycle("No classes found matching '${config.pattern}'")
            return
        }

        val cacheFile = File(project.layout.buildDirectory.asFile.get(), "cnav/call-graph.cache")
        val graphResult = CallGraphCache.getOrBuildTagged(cacheFile, taggedDirs)
        SkippedFileReporter.report(graphResult.skippedFiles, classReportFile)?.let { logger.warn(it) }
        val graph = graphResult.data

        val interfaceRegistry = InterfaceRegistryCache.getOrBuild(
            File(project.layout.buildDirectory.asFile.get(), "cnav/interface-registry.cache"),
            classDirectories,
        ).data
        val interfaceImplementors = interfaceRegistry.implementorMap()
        val classToInterfaces = interfaceRegistry.classToInterfacesMap()

        val annotations = AnnotationExtractor.scanAll(classDirectories)
        val filter = config.buildFilter(graph)

        val results = matchingDetails.map { classDetail ->
            val methods = classDetail.methods.map { method ->
                MethodRef(classDetail.className, method.name)
            }

            val callers = CallTreeBuilder.build(
                graph, methods, config.maxDepth, CallDirection.CALLERS, filter,
                interfaceImplementors = interfaceImplementors,
                classToInterfaces = classToInterfaces,
                classAnnotations = annotations.classAnnotations,
                methodAnnotations = annotations.methodAnnotations,
                classAnnotationParameters = annotations.classAnnotationParameters,
                methodAnnotationParameters = annotations.methodAnnotationParameters,
            )

            val callees = CallTreeBuilder.build(
                graph, methods, config.maxDepth, CallDirection.CALLEES, filter,
                interfaceImplementors = interfaceImplementors,
                classToInterfaces = classToInterfaces,
                classAnnotations = annotations.classAnnotations,
                methodAnnotations = annotations.methodAnnotations,
                classAnnotationParameters = annotations.classAnnotationParameters,
                methodAnnotationParameters = annotations.methodAnnotationParameters,
            )

            val implementors = interfaceRegistry.implementorsOf(classDetail.className)
            val implementedInterfaces = interfaceRegistry.interfacesOf(classDetail.className).sorted().toList()

            ContextBuilder.build(
                classDetail = classDetail,
                callers = callers,
                callees = callees,
                implementors = implementors,
                implementedInterfaces = implementedInterfaces,
            )
        }

        logger.lifecycle(
            OutputWrapper.formatAndWrap(
                config.format,
                text = { results.joinToString("\n\n") { ContextFormatter.format(it) } },
                json = { "[${results.joinToString(",") { JsonFormatter.formatContext(it) }}]" },
                llm = { results.joinToString("\n\n") { LlmFormatter.formatContext(it) } },
            ),
        )
    }
}
