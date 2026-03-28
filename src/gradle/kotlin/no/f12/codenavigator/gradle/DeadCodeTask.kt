package no.f12.codenavigator.gradle

import no.f12.codenavigator.JsonFormatter
import no.f12.codenavigator.LlmFormatter
import no.f12.codenavigator.config.OutputFormat
import no.f12.codenavigator.OutputWrapper
import no.f12.codenavigator.TaskRegistry
import no.f12.codenavigator.navigation.AnnotationExtractor
import no.f12.codenavigator.navigation.CallGraphCache
import no.f12.codenavigator.navigation.ClassName
import no.f12.codenavigator.navigation.DeadCodeConfig
import no.f12.codenavigator.navigation.DeadCodeFinder
import no.f12.codenavigator.navigation.DeadCodeFormatter
import no.f12.codenavigator.navigation.FieldExtractor
import no.f12.codenavigator.navigation.InlineMethodDetector
import no.f12.codenavigator.navigation.InterfaceRegistryCache
import no.f12.codenavigator.navigation.SkippedFileReporter

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.File

@DisableCachingByDefault(because = "Produces console output only")
abstract class DeadCodeTask : DefaultTask() {

    @TaskAction
    fun showDeadCode() {
        val config = DeadCodeConfig.parse(
            project.buildPropertyMap(TaskRegistry.DEAD),
        )

        val sourceSets = project.extensions.getByType(SourceSetContainer::class.java)
        val mainSourceSet = sourceSets.getByName("main")
        val classDirectories = mainSourceSet.output.classesDirs.files.toList()

        val cacheFile = File(project.layout.buildDirectory.asFile.get(), "cnav/call-graph.cache")
        val result = CallGraphCache.getOrBuild(cacheFile, classDirectories)
        val reportFile = File(project.layout.buildDirectory.asFile.get(), "cnav/skipped-files.txt")
        SkippedFileReporter.report(result.skippedFiles, reportFile)?.let { logger.warn(it) }
        val graph = result.data

        val excludeAnnotated = config.excludeAnnotated.toSet()
        val (classAnnotations, methodAnnotations) = AnnotationExtractor.scanAll(classDirectories)

        val testSourceSet = sourceSets.findByName("test")
        val testClassDirectories = testSourceSet?.output?.classesDirs?.files?.filter { it.exists() }?.toList() ?: emptyList()
        val testGraph = if (testClassDirectories.isNotEmpty()) {
            CallGraphCache.getOrBuild(
                File(project.layout.buildDirectory.asFile.get(), "cnav/test-call-graph.cache"),
                testClassDirectories,
            ).data
        } else {
            null
        }

        val interfaceRegistry = InterfaceRegistryCache.getOrBuild(
            File(project.layout.buildDirectory.asFile.get(), "cnav/interface-registry.cache"),
            classDirectories,
        ).data
        val interfaceImplementors = mutableMapOf<ClassName, MutableSet<ClassName>>()
        interfaceRegistry.forEachEntry { interfaceName, implementors ->
            interfaceImplementors[interfaceName] = implementors.map { it.className }.toMutableSet()
        }

        val classFields = FieldExtractor.scanAll(classDirectories)

        val inlineMethods = InlineMethodDetector.scanAll(classDirectories)

        val classExternalInterfaces = interfaceRegistry.externalInterfacesOf(graph.projectClasses())

        val dead = DeadCodeFinder.find(
            graph = graph,
            filter = config.filter,
            exclude = config.exclude,
            classesOnly = config.classesOnly,
            excludeAnnotated = excludeAnnotated,
            classAnnotations = classAnnotations,
            methodAnnotations = methodAnnotations,
            testGraph = testGraph,
            interfaceImplementors = interfaceImplementors,
            classFields = classFields,
            inlineMethods = inlineMethods,
            classExternalInterfaces = classExternalInterfaces,
            prodOnly = config.prodOnly,
        )

        if (dead.isEmpty()) {
            logger.lifecycle("No potential dead code found.")
            return
        }

        val output = when (config.format) {
            OutputFormat.JSON -> JsonFormatter.formatDead(dead)
            OutputFormat.LLM -> LlmFormatter.formatDead(dead)
            OutputFormat.TEXT -> DeadCodeFormatter.format(dead)
        }
        logger.lifecycle(OutputWrapper.wrap(output, config.format))
    }
}
