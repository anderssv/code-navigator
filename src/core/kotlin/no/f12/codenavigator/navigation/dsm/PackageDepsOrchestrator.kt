package no.f12.codenavigator.navigation.dsm

import no.f12.codenavigator.navigation.bytecode.SkippedFileReporter
import no.f12.codenavigator.navigation.relations.callgraph.CallGraphCache
import no.f12.codenavigator.navigation.relations.callgraph.MethodRef
import no.f12.codenavigator.navigation.types.PackageName
import no.f12.codenavigator.navigation.types.Scope
import no.f12.codenavigator.navigation.types.SourceSet
import java.io.File

data class PackageDepsOutput(
    val deps: PackageDependencies,
    val packages: List<PackageName>,
    val skippedFileWarning: String?,
    /** Non-null when there is nothing to render; [packages] is then empty. */
    val emptyMessage: String? = null,
    val emptyHints: List<String> = emptyList(),
)

/** Shared by PackageDepsTask (Gradle) and PackageDepsMojo (Maven) so both build tools run the exact same pipeline. */
object PackageDepsOrchestrator {

    fun run(
        config: PackageDepsConfig,
        taggedDirs: List<Pair<File, SourceSet>>,
        cacheDir: File,
        reportFile: File,
    ): PackageDepsOutput {
        val scopedDirs = taggedDirs.filter { config.scope.matchesSourceSet(it.second) }

        // Scope-specific cache file: the shared `call-graph.cache` is written by tasks that scan every
        // source set, and CacheFreshness only compares timestamps — a --scope=prod run would otherwise
        // read back a graph containing test classes and report test packages as production dependencies.
        val result = CallGraphCache.getOrBuildTagged(
            CallGraphCache.cacheFileFor(cacheDir, config.scope),
            scopedDirs,
        )
        val skippedFileWarning = SkippedFileReporter.report(result.skippedFiles, reportFile)
        val graph = result.data

        val filter: ((MethodRef) -> Boolean)? =
            if (config.projectOnly) graph.projectClassFilter() else null

        val deps = PackageDependencyBuilder.build(graph, filter)

        if (config.packagePattern != null) {
            val matches = deps.findPackages(config.packagePattern)
            return if (matches.isEmpty()) {
                PackageDepsOutput(
                    deps,
                    emptyList(),
                    skippedFileWarning,
                    emptyMessage = "No packages found matching '${config.packagePattern}'",
                )
            } else {
                PackageDepsOutput(deps, matches, skippedFileWarning)
            }
        }

        val all = deps.allPackages()
        if (all.isEmpty()) {
            val packageCount = graph.projectClasses().map { it.packageName() }.distinct().size
            return PackageDepsOutput(
                deps,
                emptyList(),
                skippedFileWarning,
                emptyMessage = "No inter-package dependencies found.",
                emptyHints = PackageDependencyFormatter.noResultsHints(packageCount),
            )
        }
        return PackageDepsOutput(deps, all, skippedFileWarning)
    }
}
