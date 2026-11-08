package no.f12.codenavigator.navigation.branches

import no.f12.codenavigator.navigation.relations.callgraph.MethodRef

sealed interface PlacedItem {
    val line: Int
}

data class PlacedBranch(val kind: BranchKind, val label: String, override val line: Int, val children: List<PlacedItem>) : PlacedItem

data class PlacedCall(val callee: MethodRef, override val line: Int) : PlacedItem

data class PlacedExit(val kind: ExitKind, val text: String, override val line: Int) : PlacedItem

/**
 * A method's callees laid out in its branches. [unplaced] are callees no call in the source matched;
 * [lambdas] are bodies of lambdas written in the method (compiler-made classes), which have no call site.
 */
data class PlacedBody(val items: List<PlacedItem>, val unplaced: List<MethodRef>, val lambdas: List<MethodRef> = emptyList())

/**
 * Puts bytecode callees into the branches of the source function they were compiled from. Matching is by
 * name only (no type resolution): `cache.delete()` and `repo.delete()` both match every `delete` callee.
 * Branches with nothing placed and no exit are dropped.
 */
object BranchPlacer {

    private val LAMBDA_SUFFIX = Regex("\\\$lambda\\\$\\d+\$")
    private const val DEFAULT_SUFFIX = "\$default"

    fun place(function: FunctionBranches, callees: Collection<MethodRef>): PlacedBody {
        val placed = mutableSetOf<MethodRef>()
        val items = placeItems(function.body, callees, placed)
        val (lambdas, unplaced) = callees.filter { it !in placed }.distinct().sortedBy { it.qualifiedName }
            .partition { it.className.isSynthetic() }
        return PlacedBody(items, unplaced, lambdas)
    }

    private fun placeItems(items: List<BranchItem>, callees: Collection<MethodRef>, placed: MutableSet<MethodRef>): List<PlacedItem> {
        val out = mutableListOf<PlacedItem>()
        val seenHere = mutableSetOf<MethodRef>()
        for (item in items) {
            when (item) {
                is CallSite -> callees.filter { matches(item, it) }.sortedBy { it.qualifiedName }.forEach { callee ->
                    placed += callee
                    if (seenHere.add(callee)) out += PlacedCall(callee, item.line)
                }
                is Exit -> out += PlacedExit(item.kind, item.text, item.line)
                is Branch -> {
                    val children = placeItems(item.children, callees, placed)
                    if (children.isNotEmpty()) out += PlacedBranch(item.kind, item.label, item.line, children)
                }
            }
        }
        return out
    }

    /** The source function a bytecode method was compiled from: same class and name, overloads told apart by [firstLine]. */
    fun functionFor(method: MethodRef, firstLine: Int?, functions: List<FunctionBranches>): FunctionBranches? {
        val name = sourceName(method.methodName)
        val candidates = functions.filter { it.className == method.className.value && it.name == name }
            .ifEmpty { enclosingFunctions(method.className.value, functions) }
        if (firstLine == null) return candidates.firstOrNull()
        return candidates.firstOrNull { firstLine in it.startLine..it.endLine } ?: candidates.firstOrNull()
    }

    /**
     * For a method of a compiler-made class (`p.Svc$removeAll$2`, `p.Svc$Inner$run$1$1`): the function whose
     * body the class was written in (`p.Svc.removeAll`, `p.Svc$Inner.run`). Tries the longest class prefix first.
     */
    private fun enclosingFunctions(className: String, functions: List<FunctionBranches>): List<FunctionBranches> {
        val segments = className.split('$')
        for (i in segments.size - 2 downTo 0) {
            val owner = segments.subList(0, i + 1).joinToString("$")
            val found = functions.filter { it.className == owner && it.name == segments[i + 1] }
            if (found.isNotEmpty()) return found
        }
        return emptyList()
    }

    /**
     * The branch labels (outside in) around each call of [callee] in [function]: one list per call, an empty
     * list for an unconditional call, null when no call matches.
     */
    fun conditionsFor(function: FunctionBranches, callee: MethodRef): List<List<String>>? {
        val chains = mutableListOf<List<String>>()
        fun walk(items: List<BranchItem>, chain: List<String>) {
            for (item in items) {
                when (item) {
                    is CallSite -> if (matches(item, callee)) chains += chain
                    is Branch -> walk(item.children, chain + item.label)
                    is Exit -> {}
                }
            }
        }
        walk(function.body, emptyList())
        return chains.distinct().ifEmpty { null }
    }

    private fun sourceName(methodName: String): String =
        methodName.replace(LAMBDA_SUFFIX, "").removeSuffix(DEFAULT_SUFFIX)

    private fun matches(site: CallSite, callee: MethodRef): Boolean {
        val method = callee.methodName
        if (method == "<init>") return !site.property && site.name == callee.className.value.substringAfterLast('.').substringAfterLast('$')
        if (!site.property) return method == site.name
        val capitalized = site.name.replaceFirstChar { it.uppercase() }
        val isProperty = site.name.startsWith("is") && site.name.length > 2 && site.name[2].isUpperCase()
        return method == site.name ||
            method == "get$capitalized" ||
            method == "set$capitalized" ||
            (isProperty && method == "set" + site.name.drop(2))
    }
}
