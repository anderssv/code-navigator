package no.f12.codenavigator.navigation.context

import no.f12.codenavigator.navigation.annotation.AnnotationExtractor
import no.f12.codenavigator.navigation.bytecode.SkippedFileReporter
import no.f12.codenavigator.navigation.classinfo.ClassDetailScanner
import no.f12.codenavigator.navigation.relations.callgraph.CallDirection
import no.f12.codenavigator.navigation.relations.callgraph.CallGraphCache
import no.f12.codenavigator.navigation.relations.callgraph.CallTreeBuilder
import no.f12.codenavigator.navigation.relations.callgraph.MethodRef
import no.f12.codenavigator.navigation.relations.implementors.InterfaceRegistryCache
import no.f12.codenavigator.navigation.types.SourceSet
import java.io.File

data class ContextOutput(
    val results: List<ContextResult>,
    /** Reported independently for the class scan and the call-graph build — either or both may be present. */
    val skippedFileWarnings: List<String>,
)

/** Shared by ContextTask (Gradle) and ContextMojo (Maven) so both build tools run the exact same pipeline. */
object ContextOrchestrator {

    fun run(
        config: ContextConfig,
        taggedDirs: List<Pair<File, SourceSet>>,
        cacheDir: File,
    ): ContextOutput {
        val classDirectories = taggedDirs.map { it.first }
        val reportFile = File(cacheDir, "skipped-files.txt")

        val classResult = ClassDetailScanner.scan(classDirectories, config.pattern)
        val warnings = mutableListOf<String>()
        SkippedFileReporter.report(classResult.skippedFiles, reportFile)?.let { warnings.add(it) }
        val matchingDetails = classResult.data

        if (matchingDetails.isEmpty()) {
            return ContextOutput(emptyList(), warnings)
        }

        val graphResult = CallGraphCache.getOrBuildTagged(File(cacheDir, "call-graph.cache"), taggedDirs)
        SkippedFileReporter.report(graphResult.skippedFiles, reportFile)?.let { warnings.add(it) }
        val graph = graphResult.data

        val interfaceRegistry = InterfaceRegistryCache.getOrBuild(
            File(cacheDir, "interface-registry.cache"),
            classDirectories,
        ).data
        val interfaceImplementors = interfaceRegistry.implementorMap()
        val classToInterfaces = interfaceRegistry.classToInterfacesMap()

        val annotations = AnnotationExtractor.scanAll(classDirectories)
        val callersFilter = config.buildFilter(graph, CallDirection.CALLERS)
        val calleesFilter = config.buildFilter(graph, CallDirection.CALLEES)

        val results = matchingDetails.map { classDetail ->
            val methods = classDetail.methods.map { method ->
                MethodRef(classDetail.className, method.name)
            }

            val callers = CallTreeBuilder.build(
                graph, methods, config.maxDepth, CallDirection.CALLERS, callersFilter,
                interfaceImplementors = interfaceImplementors,
                classToInterfaces = classToInterfaces,
                classAnnotations = annotations.classAnnotations,
                methodAnnotations = annotations.methodAnnotations,
                classAnnotationParameters = annotations.classAnnotationParameters,
                methodAnnotationParameters = annotations.methodAnnotationParameters,
            )

            val callees = CallTreeBuilder.build(
                graph, methods, config.maxDepth, CallDirection.CALLEES, calleesFilter,
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

        return ContextOutput(results, warnings)
    }
}
