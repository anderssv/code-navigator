package no.f12.codenavigator.gradle

import no.f12.codenavigator.config.OutputFormat

import no.f12.codenavigator.registry.BuildTool
import no.f12.codenavigator.formatting.JsonFormatter
import no.f12.codenavigator.formatting.LlmFormatter
import no.f12.codenavigator.formatting.OutputWrapper
import no.f12.codenavigator.registry.TaskRegistry
import no.f12.codenavigator.navigation.bytecode.SkippedFileReporter
import no.f12.codenavigator.navigation.annotation.AnnotationExtractor
import no.f12.codenavigator.navigation.relations.callgraph.CallDirection
import no.f12.codenavigator.navigation.relations.callgraph.CallGraphCache
import no.f12.codenavigator.navigation.relations.callgraph.CallTreeBuilder
import no.f12.codenavigator.navigation.relations.callgraph.MethodRef
import no.f12.codenavigator.navigation.classinfo.ClassDetailScanner
import no.f12.codenavigator.navigation.context.ContextBuilder
import no.f12.codenavigator.navigation.context.ContextConfig
import no.f12.codenavigator.navigation.context.ContextFormatter
import no.f12.codenavigator.navigation.relations.implementors.InterfaceRegistryCache

import org.gradle.api.GradleException
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import org.gradle.work.DisableCachingByDefault
import java.io.File

@DisableCachingByDefault(because = "Produces console output only")
abstract class ContextTask : CodeNavigatorTask() {

    @Option(option = "pattern", description = "Class/symbol name regex (camelCase-aware: MyService matches com.example.MyService)")
    @get:Internal
    var pattern: String? = null

    @Option(option = "maxdepth", description = "Max call tree depth (default: 2)")
    @get:Internal
    var maxdepth: String? = null

    @Option(option = "project-only", description = "Hide JDK/stdlib/library classes (default: on)")
    @get:Internal
    var projectOnly: String? = null

    @Option(option = "filter-synthetic", description = "Set false to include synthetic methods (equals, hashCode, copy, componentN, etc.)")
    @get:Internal
    var filterSynthetic: String? = null

    @Option(option = "scope", description = "Filter by source set: all (default), prod (production only), test (test only)")
    @get:Internal
    var scope: String? = null

    override fun taskOptionsMap(): Map<String, String?> = buildMap {
        pattern?.let { put("pattern", it) }
        maxdepth?.let { put("maxdepth", it) }
        projectOnly?.let { put("project-only", it) }
        filterSynthetic?.let { put("filter-synthetic", it) }
        scope?.let { put("scope", it) }
    }

    @TaskAction
    fun gatherContext() {
        val config = try {
            ContextConfig.parse(TaskRegistry.CONTEXT.enhanceProperties(buildOptionsMap()))
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
            logger.lifecycle(OutputWrapper.emptyResult(config.format, "No classes found matching '${config.pattern}'"))
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
            OutputWrapper.formatAndWrap(config.format) { format ->
                when (format) {
                    OutputFormat.TEXT, OutputFormat.DIFF -> results.joinToString("\n\n") { ContextFormatter.format(it) }
                    OutputFormat.JSON -> "[${results.joinToString(",") { JsonFormatter.formatContext(it) }}]"
                    OutputFormat.LLM -> results.joinToString("\n\n") { LlmFormatter.formatContext(it) }
                }
            },
        )
    }
}
