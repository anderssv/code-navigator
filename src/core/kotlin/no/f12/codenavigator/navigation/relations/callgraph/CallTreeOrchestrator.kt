package no.f12.codenavigator.navigation.relations.callgraph

import no.f12.codenavigator.navigation.annotation.AnnotationExtractor
import no.f12.codenavigator.navigation.bytecode.SkippedFileReporter
import no.f12.codenavigator.navigation.relations.implementors.InterfaceRegistryCache
import no.f12.codenavigator.navigation.types.SourceSet
import java.io.File

data class CallTreeOutput(
    val trees: List<CallTreeNode>,
    val classHint: String?,
    val skippedFileWarning: String?,
)

/** Shared by CallTreeTaskSupport (Gradle, find-callers/find-callees) and CallTreeMojoSupport (Maven) so both build tools run the exact same pipeline. */
object CallTreeOrchestrator {

    fun run(
        config: CallGraphConfig,
        taggedDirs: List<Pair<File, SourceSet>>,
        cacheDir: File,
        direction: CallDirection,
    ): CallTreeOutput {
        val classDirectories = taggedDirs.map { it.first }

        val result = CallGraphCache.getOrBuildTagged(File(cacheDir, "call-graph.cache"), taggedDirs)
        val skippedFileWarning = SkippedFileReporter.report(result.skippedFiles, File(cacheDir, "skipped-files.txt"))
        val graph = result.data
        val methods = graph.findMethods(config.method)

        if (methods.isEmpty()) {
            return CallTreeOutput(emptyList(), classHint = null, skippedFileWarning = skippedFileWarning)
        }

        val interfaceRegistry = InterfaceRegistryCache.getOrBuild(
            File(cacheDir, "interface-registry.cache"),
            classDirectories,
        ).data
        val interfaceImplementors = interfaceRegistry.implementorMap()
        val classToInterfaces = interfaceRegistry.classToInterfacesMap()

        val annotations = AnnotationExtractor.scanAll(classDirectories)

        val trees = CallTreeBuilder.build(
            graph, methods, config.maxDepth, direction, config.buildFilter(graph, direction),
            interfaceImplementors = interfaceImplementors,
            classToInterfaces = classToInterfaces,
            classAnnotations = annotations.classAnnotations,
            methodAnnotations = annotations.methodAnnotations,
            classAnnotationParameters = annotations.classAnnotationParameters,
            methodAnnotationParameters = annotations.methodAnnotationParameters,
            maxImplementors = config.maxImplementors,
        )

        val classHint = CallTreeFormatter.classMatchHint(config.method, methods)

        return CallTreeOutput(trees, classHint, skippedFileWarning)
    }
}
