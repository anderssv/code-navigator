package no.f12.codenavigator

class PackageDependencies(
    private val packageToDeps: Map<String, List<String>>,
) {
    fun dependenciesOf(packageName: String): List<String> =
        packageToDeps[packageName] ?: emptyList()

    fun findPackages(pattern: String): List<String> {
        val regex = Regex(pattern, RegexOption.IGNORE_CASE)
        return packageToDeps.keys
            .filter { regex.containsMatchIn(it) }
            .sorted()
    }

    fun allPackages(): List<String> = packageToDeps.keys.sorted()
}

object PackageDependencyBuilder {

    fun build(graph: CallGraph, filter: ((MethodRef) -> Boolean)? = null): PackageDependencies {
        val packageDeps = mutableMapOf<String, MutableSet<String>>()

        graph.forEachEdge { caller, callee ->
            if (filter != null && (!filter(caller) || !filter(callee))) return@forEachEdge

            val callerPackage = caller.className.substringBeforeLast('.', "")
            val calleePackage = callee.className.substringBeforeLast('.', "")

            if (callerPackage.isNotEmpty() && calleePackage.isNotEmpty() && callerPackage != calleePackage) {
                packageDeps.getOrPut(callerPackage) { mutableSetOf() }.add(calleePackage)
            }
        }

        val sorted = packageDeps.mapValues { (_, deps) -> deps.sorted() }
        return PackageDependencies(sorted)
    }
}
