package no.f12.codenavigator.gradle

import no.f12.codenavigator.JsonFormatter
import no.f12.codenavigator.LlmFormatter
import no.f12.codenavigator.OutputWrapper
import no.f12.codenavigator.registry.TaskRegistry
import no.f12.codenavigator.navigation.annotation.AnnotationExtractor
import no.f12.codenavigator.navigation.callgraph.CallGraphCache
import no.f12.codenavigator.navigation.ClassName
import no.f12.codenavigator.navigation.deadcode.BridgeMethodDetector
import no.f12.codenavigator.navigation.deadcode.DeadCodeConfig
import no.f12.codenavigator.navigation.deadcode.DeadCodeFinder
import no.f12.codenavigator.navigation.deadcode.DeadCodeFormatter
import no.f12.codenavigator.navigation.deadcode.DelegationMethodDetector
import no.f12.codenavigator.navigation.deadcode.FieldExtractor
import no.f12.codenavigator.navigation.deadcode.InlineMethodDetector
import no.f12.codenavigator.navigation.deadcode.ReceiverTypeExtractor
import no.f12.codenavigator.navigation.interfaces.InterfaceRegistryCache
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
        val annotations = AnnotationExtractor.scanAll(classDirectories)

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

        val delegationMethods = DelegationMethodDetector.scanAll(classDirectories)

        val bridgeMethods = BridgeMethodDetector.scanAll(classDirectories)

        val classExternalInterfaces = interfaceRegistry.externalInterfacesOf(graph.projectClasses())

        val classReceiverTypes = ReceiverTypeExtractor.scanAll(classDirectories)

        val dead = DeadCodeFinder.find(
            graph = graph,
            filter = config.filter,
            exclude = config.exclude,
            classesOnly = config.classesOnly,
            excludeAnnotated = excludeAnnotated,
            classAnnotations = annotations.classAnnotations,
            methodAnnotations = annotations.methodAnnotations,
            testGraph = testGraph,
            interfaceImplementors = interfaceImplementors,
            classFields = classFields,
            inlineMethods = inlineMethods,
            classExternalInterfaces = classExternalInterfaces,
            prodOnly = config.prodOnly,
            modifierAnnotated = config.modifierAnnotated.toSet(),
            supertypeEntryPoints = config.supertypeEntryPoints,
            testClasses = testGraph?.projectClasses() ?: emptySet(),
            classReceiverTypes = classReceiverTypes,
            receiverTypeEntryPoints = config.receiverTypeEntryPoints,
            testOnly = config.testOnly,
            delegationMethods = delegationMethods,
            bridgeMethods = bridgeMethods,
        )

        if (dead.isEmpty()) {
            logger.lifecycle(OutputWrapper.emptyResult(config.format, "No potential dead code found."))
            return
        }

        logger.lifecycle(OutputWrapper.formatAndWrap(config.format,
            text = { DeadCodeFormatter.format(dead) },
            json = { JsonFormatter.formatDead(dead) },
            llm = { LlmFormatter.formatDead(dead) },
        ))
    }
}
