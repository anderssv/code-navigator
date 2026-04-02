package no.f12.codenavigator.navigation

import no.f12.codenavigator.navigation.core.ClassName
import no.f12.codenavigator.navigation.callgraph.CallGraph
import no.f12.codenavigator.navigation.callgraph.MethodRef

/**
 * Shared test utility for constructing in-memory [CallGraph] instances from edge pairs.
 *
 * Used by tests that don't need real bytecode — they work directly with the call graph
 * data structure (e.g. DeadCodeFinder, TypeRanker, ClassComplexityAnalyzer).
 *
 * ## Usage
 * ```
 * val graph = testCallGraph(
 *     method("com.example.Controller", "handle") to method("com.example.Service", "process"),
 *     method("com.example.Service", "process") to method("com.example.Repo", "save"),
 *     projectClasses = setOf("com.example.Controller", "com.example.Service", "com.example.Repo"),
 * )
 * ```
 */
fun testCallGraph(
    vararg edges: Pair<MethodRef, MethodRef>,
    projectClasses: Set<String> = emptySet(),
): CallGraph {
    val callerToCallees = mutableMapOf<MethodRef, MutableSet<MethodRef>>()
    val sourceFiles = mutableMapOf<ClassName, String>()

    for ((caller, callee) in edges) {
        callerToCallees.getOrPut(caller) { mutableSetOf() }.add(callee)
    }

    val allClasses = edges.flatMap { listOf(it.first.className.value, it.second.className.value) }.toSet()
    val classesWithSource = if (projectClasses.isNotEmpty()) projectClasses else allClasses
    for (cls in classesWithSource) {
        sourceFiles[ClassName(cls)] = "${cls.substringAfterLast('.')}.kt"
    }

    return CallGraph(callerToCallees, sourceFiles)
}

/** Shorthand to create a [MethodRef] from class name and method name strings. */
fun method(className: String, methodName: String) = MethodRef(ClassName(className), methodName)
