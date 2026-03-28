package no.f12.codenavigator.gradle

import no.f12.codenavigator.JsonFormatter
import no.f12.codenavigator.LlmFormatter
import no.f12.codenavigator.OutputWrapper
import no.f12.codenavigator.TaskRegistry
import no.f12.codenavigator.navigation.annotation.AnnotationExtractor
import no.f12.codenavigator.navigation.CallDirection
import no.f12.codenavigator.navigation.CallGraphCache
import no.f12.codenavigator.navigation.CallGraphConfig
import no.f12.codenavigator.navigation.CallTreeBuilder
import no.f12.codenavigator.navigation.CallTreeFormatter
import no.f12.codenavigator.navigation.interfaces.InterfaceRegistryCache
import no.f12.codenavigator.navigation.SkippedFileReporter

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.File

@DisableCachingByDefault(because = "Produces console output only")
abstract class FindCallersTask : DefaultTask() {

    @TaskAction
    fun findCallers() {
        val config = try {
            CallGraphConfig.parse(
                project.buildPropertyMap(TaskRegistry.FIND_CALLERS),
            )
        } catch (e: IllegalArgumentException) {
            throw GradleException(
                "Missing required property. Usage: ./gradlew cnavCallers -Pmethod=<regex> -Pmaxdepth=3",
            )
        }

        val sourceSets = project.extensions.getByType(SourceSetContainer::class.java)
        val mainSourceSet = sourceSets.getByName("main")
        val classDirectories = mainSourceSet.output.classesDirs.files.toList()

        val cacheFile = File(project.layout.buildDirectory.asFile.get(), "cnav/call-graph.cache")
        val result = CallGraphCache.getOrBuild(cacheFile, classDirectories)
        val reportFile = File(project.layout.buildDirectory.asFile.get(), "cnav/skipped-files.txt")
        SkippedFileReporter.report(result.skippedFiles, reportFile)?.let { logger.warn(it) }
        val graph = result.data
        val methods = graph.findMethods(config.method)

        if (methods.isEmpty()) {
            logger.lifecycle("No methods found matching '${config.method}'")
            return
        }

        val interfaceRegistry = InterfaceRegistryCache.getOrBuild(
            File(project.layout.buildDirectory.asFile.get(), "cnav/interface-registry.cache"),
            classDirectories,
        ).data
        val interfaceImplementors = interfaceRegistry.implementorMap()
        val classToInterfaces = interfaceRegistry.classToInterfacesMap()

        val (classAnnotations, methodAnnotations) = AnnotationExtractor.scanAll(classDirectories)

        val trees = CallTreeBuilder.build(
            graph, methods, config.maxDepth, CallDirection.CALLERS, config.buildFilter(graph),
            interfaceImplementors = interfaceImplementors,
            classToInterfaces = classToInterfaces,
            classAnnotations = classAnnotations,
            methodAnnotations = methodAnnotations,
        )
        logger.lifecycle(OutputWrapper.formatAndWrap(config.format,
            text = { CallTreeFormatter.renderTrees(trees, CallDirection.CALLERS) },
            json = { JsonFormatter.renderCallTrees(trees) },
            llm = { LlmFormatter.renderCallTrees(trees, CallDirection.CALLERS) },
        ))
    }
}
