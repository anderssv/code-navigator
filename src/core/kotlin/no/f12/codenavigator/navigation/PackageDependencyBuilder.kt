package no.f12.codenavigator.navigation

class PackageDependencies(
    private val packageToDeps: Map<String, List<String>>,
) {
    private val packageToDependents: Map<String, List<String>> by lazy {
        val inverted = mutableMapOf<String, MutableSet<String>>()
        packageToDeps.forEach { (pkg, deps) ->
            deps.forEach { dep ->
                inverted.getOrPut(dep) { mutableSetOf() }.add(pkg)
            }
        }
        inverted.mapValues { (_, dependents) -> dependents.sorted() }
    }

    fun dependenciesOf(packageName: String): List<String> =
        packageToDeps[packageName] ?: emptyList()

    fun dependentsOf(packageName: String): List<String> =
        packageToDependents[packageName] ?: emptyList()

    fun findPackages(pattern: String): List<String> {
        val regex = Regex(pattern, RegexOption.IGNORE_CASE)
        return allPackages()
            .filter { regex.containsMatchIn(it) }
    }

    fun allPackages(): List<String> {
        val all = mutableSetOf<String>()
        all.addAll(packageToDeps.keys)
        packageToDeps.values.forEach { all.addAll(it) }
        return all.sorted()
    }
}

object PackageDependencyBuilder {

    fun build(graph: CallGraph, filter: ((MethodRef) -> Boolean)? = null): PackageDependencies {
        val packageDeps = mutableMapOf<String, MutableSet<String>>()

        graph.forEachEdge { caller, callee ->
            if (filter != null && (!filter(caller) || !filter(callee))) return@forEachEdge

            val callerPackage = caller.className.value.substringBeforeLast('.', "")
            val calleePackage = callee.className.value.substringBeforeLast('.', "")

            if (callerPackage.isNotEmpty() && calleePackage.isNotEmpty() && callerPackage != calleePackage) {
                packageDeps.getOrPut(callerPackage) { mutableSetOf() }.add(calleePackage)
            }
        }

        val sorted = packageDeps.mapValues { (_, deps) -> deps.sorted() }
        return PackageDependencies(sorted)
    }
}
