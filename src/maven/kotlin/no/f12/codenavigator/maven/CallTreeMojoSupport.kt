package no.f12.codenavigator.maven

import no.f12.codenavigator.formatting.JsonFormatter
import no.f12.codenavigator.formatting.LlmFormatter
import no.f12.codenavigator.formatting.OutputWrapper
import no.f12.codenavigator.registry.TaskDef
import no.f12.codenavigator.navigation.core.SkippedFileReporter
import no.f12.codenavigator.navigation.annotation.AnnotationExtractor
import no.f12.codenavigator.navigation.callgraph.CallDirection
import no.f12.codenavigator.navigation.callgraph.CallGraphCache
import no.f12.codenavigator.navigation.callgraph.CallGraphConfig
import no.f12.codenavigator.navigation.callgraph.CallTreeBuilder
import no.f12.codenavigator.navigation.callgraph.CallTreeFormatter
import no.f12.codenavigator.navigation.interfaces.InterfaceRegistryCache
import org.apache.maven.plugin.MojoFailureException
import org.apache.maven.plugin.logging.Log
import org.apache.maven.project.MavenProject
import java.io.File

object CallTreeMojoSupport {

    fun execute(
        project: MavenProject,
        log: Log,
        properties: Map<String, String?>,
        taskDef: TaskDef,
        direction: CallDirection,
        usageHint: String,
    ) {
        val config = try {
            CallGraphConfig.parse(taskDef.enhanceProperties(properties))
        } catch (e: IllegalArgumentException) {
            throw MojoFailureException(usageHint)
        }

        val taggedDirs = project.taggedClassDirectories()
        if (taggedDirs.isEmpty()) {
            log.warn("Classes directory does not exist: ${File(project.build.outputDirectory)} — run 'mvn compile' first.")
            return
        }
        val classDirectories = taggedDirs.map { it.first }

        val result = CallGraphCache.getOrBuildTagged(File(project.build.directory, "cnav/call-graph.cache"), taggedDirs)
        val reportFile = File(project.build.directory, "cnav/skipped-files.txt")
        SkippedFileReporter.report(result.skippedFiles, reportFile)?.let { log.warn(it) }
        val graph = result.data
        val methods = graph.findMethods(config.method)

        if (methods.isEmpty()) {
            println("No methods found matching '${config.method}'")
            return
        }

        val interfaceRegistry = InterfaceRegistryCache.getOrBuild(
            File(project.build.directory, "cnav/interface-registry.cache"),
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
        println(
            OutputWrapper.formatAndWrap(
                config.format,
                text = { CallTreeFormatter.renderTrees(trees, direction) },
                json = { JsonFormatter.renderCallTrees(trees, direction) },
                llm = { LlmFormatter.renderCallTrees(trees, direction) },
            ),
        )
    }
}
