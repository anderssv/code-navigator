package no.f12.codenavigator.navigation.dsm

import no.f12.codenavigator.navigation.PackageName
import no.f12.codenavigator.navigation.callgraph.CallGraph
import no.f12.codenavigator.navigation.callgraph.MethodRef

class PackageDependencies(
    private val packageToDeps: Map<PackageName, List<PackageName>>,
) {
    private val packageToDependents: Map<PackageName, List<PackageName>> by lazy {
        val inverted = mutableMapOf<PackageName, MutableSet<PackageName>>()
        packageToDeps.forEach { (pkg, deps) ->
            deps.forEach { dep ->
                inverted.getOrPut(dep) { mutableSetOf() }.add(pkg)
            }
        }
        inverted.mapValues { (_, dependents) -> dependents.sorted() }
    }

    fun dependenciesOf(packageName: PackageName): List<PackageName> =
        packageToDeps[packageName] ?: emptyList()

    fun dependentsOf(packageName: PackageName): List<PackageName> =
        packageToDependents[packageName] ?: emptyList()

    fun findPackages(pattern: String): List<PackageName> {
        val regex = Regex(pattern, RegexOption.IGNORE_CASE)
        return allPackages()
            .filter { it.matches(regex) }
    }

    fun allPackages(): List<PackageName> {
        val all = mutableSetOf<PackageName>()
        all.addAll(packageToDeps.keys)
        packageToDeps.values.forEach { all.addAll(it) }
        return all.sorted()
    }
}

object PackageDependencyBuilder {

    fun build(graph: CallGraph, filter: ((MethodRef) -> Boolean)? = null): PackageDependencies {
        val packageDeps = mutableMapOf<PackageName, MutableSet<PackageName>>()

        graph.forEachEdge { caller, callee ->
            if (filter != null && (!filter(caller) || !filter(callee))) return@forEachEdge

            val callerPackage = caller.className.packageName()
            val calleePackage = callee.className.packageName()

            if (callerPackage.isNotEmpty() && calleePackage.isNotEmpty() && callerPackage != calleePackage) {
                packageDeps.getOrPut(callerPackage) { mutableSetOf() }.add(calleePackage)
            }
        }

        val sorted = packageDeps.mapValues { (_, deps) -> deps.sorted() }
        return PackageDependencies(sorted)
    }
}
