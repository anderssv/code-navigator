package no.f12.codenavigator.navigation.branches

import no.f12.codenavigator.navigation.relations.callgraph.CallPathResult
import no.f12.codenavigator.navigation.relations.callgraph.CallTreeNode
import java.io.File

/**
 * How to read branches for a run: the source roots to look in and the scan to use. Gradle passes a scan that
 * runs in the PSI worker; Maven calls [BranchScanner] directly.
 */
class BranchScan(val sourceRoots: List<File>, val scan: (List<File>) -> List<FunctionBranches>)

object BranchSources {
    /** Kotlin source files under [roots] whose file name is in [names] (the call graph only records file names). */
    fun filesNamed(names: Set<String>, roots: List<File>): List<File> =
        roots.filter { it.isDirectory }
            .flatMap { root -> root.walkTopDown().filter { it.isFile && it.name in names && it.extension == "kt" }.toList() }
            .distinct()
            .sortedBy { it.path }
}

/** Adds branch information to call trees and call paths. Pure: the source has already been scanned into [FunctionBranches]. */
object BranchAnnotator {

    /** Files to scan for a tree: those of every node that has children to place. */
    fun sourceFileNames(trees: List<CallTreeNode>): Set<String> {
        val names = mutableSetOf<String>()
        fun walk(node: CallTreeNode) {
            if (node.children.isNotEmpty()) node.sourceFile?.let { names += it }
            node.children.forEach(::walk)
        }
        trees.forEach(::walk)
        return names - UNKNOWN_SOURCE
    }

    /** Files to scan for paths: those of every step that calls the next one. */
    fun sourceFileNames(result: CallPathResult): Set<String> =
        result.reaching.flatMap { it.paths }.flatMap { path -> path.dropLast(1).mapNotNull { it.sourceFile } }.toSet()

    fun annotateTrees(trees: List<CallTreeNode>, functions: List<FunctionBranches>): List<CallTreeNode> {
        val byClass = functions.groupBy { it.className }
        fun annotate(node: CallTreeNode): CallTreeNode {
            val children = node.children.map(::annotate)
            if (children.isEmpty()) return node.copy(children = children)
            val function = BranchPlacer.functionFor(node.method, node.lineNumber, byClass[node.method.className.value].orEmpty())
            val branches = function?.let { BranchPlacer.place(it, children.map { child -> child.method }) }
            return node.copy(children = children, branches = branches)
        }
        return trees.map(::annotate)
    }

    fun annotatePaths(result: CallPathResult, functions: List<FunctionBranches>): CallPathResult {
        val byClass = functions.groupBy { it.className }
        val reaching = result.reaching.map { entry ->
            entry.copy(
                paths = entry.paths.map { path ->
                    path.mapIndexed { index, step ->
                        if (index == 0) return@mapIndexed step
                        val caller = path[index - 1]
                        val function = BranchPlacer.functionFor(caller.method, caller.lineNumber, byClass[caller.method.className.value].orEmpty())
                        step.copy(conditions = function?.let { BranchPlacer.conditionsFor(it, step.method) })
                    }
                },
            )
        }
        return result.copy(reaching = reaching)
    }

    /** Scans only the source files the trees need, then annotates. */
    fun annotateTrees(trees: List<CallTreeNode>, branchScan: BranchScan): List<CallTreeNode> {
        val files = BranchSources.filesNamed(sourceFileNames(trees), branchScan.sourceRoots)
        return if (files.isEmpty()) trees else annotateTrees(trees, branchScan.scan(files))
    }

    /** Scans only the source files the paths need, then annotates. */
    fun annotatePaths(result: CallPathResult, branchScan: BranchScan): CallPathResult {
        val files = BranchSources.filesNamed(sourceFileNames(result), branchScan.sourceRoots)
        return if (files.isEmpty()) result else annotatePaths(result, branchScan.scan(files))
    }

    private const val UNKNOWN_SOURCE = "<unknown>"
}
