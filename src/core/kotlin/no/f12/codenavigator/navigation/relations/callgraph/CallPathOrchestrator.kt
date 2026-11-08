package no.f12.codenavigator.navigation.relations.callgraph

import no.f12.codenavigator.navigation.annotation.AnnotationExtractor
import no.f12.codenavigator.navigation.branches.BranchAnnotator
import no.f12.codenavigator.navigation.branches.BranchScan
import no.f12.codenavigator.navigation.bytecode.SkippedFileReporter
import no.f12.codenavigator.navigation.relations.implementors.InterfaceRegistryCache
import no.f12.codenavigator.navigation.types.Scope
import no.f12.codenavigator.navigation.types.SourceSet
import java.io.File

data class CallPathOutput(
    /** Null when no entry point or no target matched; [noMatch] then says which. */
    val report: CallPathReport?,
    val noMatch: String?,
    val skippedFileWarning: String?,
)

/** Shared by the Gradle task and the Maven mojo for call-paths, so both run the same pipeline. */
object CallPathOrchestrator {

    fun run(config: CallPathConfig, taggedDirs: List<Pair<File, SourceSet>>, cacheDir: File, branchScan: BranchScan? = null): CallPathOutput {
        val classDirectories = taggedDirs.map { it.first }
        val scan = CallGraphCache.getOrBuildTagged(File(cacheDir, "call-graph.cache"), taggedDirs)
        val skippedFileWarning = SkippedFileReporter.report(scan.skippedFiles, File(cacheDir, "skipped-files.txt"))
        val graph = scan.data
        val filter = buildFilter(graph, config)
        val annotations = AnnotationExtractor.scanAll(classDirectories)

        val entries = CallPathFinder.selectEntries(graph, config.from, config.fromAnnotationRegex(), annotations.methodAnnotations, filter)
        if (entries.isEmpty()) {
            val asked = listOfNotNull(config.from?.let { "from='$it'" }, config.fromAnnotation?.let { "from-annotation='$it'" }).joinToString(" and ")
            return CallPathOutput(null, "No entry points match $asked (scope=${config.scope.name.lowercase()})", skippedFileWarning)
        }
        val targets = graph.findMethods(config.to)
        if (targets.isEmpty()) {
            return CallPathOutput(null, "No methods match to='${config.to}'", skippedFileWarning)
        }

        val implementors = InterfaceRegistryCache.getOrBuild(File(cacheDir, "interface-registry.cache"), classDirectories).data.implementorMap()
        val result = CallPathFinder.find(
            graph, entries, targets.toSet(), config.maxDepth,
            guard = config.guardRegex(),
            guardDepth = config.guardDepth,
            filter = filter,
            interfaceImplementors = implementors,
            annotationsOf = CallPathFinder.annotationLookup(annotations),
            maxEntries = config.top,
        )
        val withBranches = if (config.branches && branchScan != null) BranchAnnotator.annotatePaths(result, branchScan) else result
        return CallPathOutput(CallPathReport(withBranches, targets, config.guard, config.maxDepth), null, skippedFileWarning)
    }

    private fun buildFilter(graph: CallGraph, config: CallPathConfig): ((MethodRef) -> Boolean)? {
        val filters = buildList<(MethodRef) -> Boolean> {
            if (config.projectOnly) add(graph.projectClassFilter())
            if (config.scope != Scope.ALL) add { config.scope.matchesSourceSet(graph.sourceSetOf(it.className) ?: SourceSet.MAIN) }
        }
        return if (filters.isEmpty()) null else { ref -> filters.all { it(ref) } }
    }
}
