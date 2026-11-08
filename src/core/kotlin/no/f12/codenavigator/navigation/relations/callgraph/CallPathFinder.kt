package no.f12.codenavigator.navigation.relations.callgraph

import no.f12.codenavigator.navigation.annotation.AggregatedAnnotations
import no.f12.codenavigator.navigation.types.AnnotationName
import no.f12.codenavigator.navigation.types.ClassName

/** One step on a call path. [via] is the interface the call was dispatched through, when it was. */
data class PathStep(
    val method: MethodRef,
    val via: ClassName? = null,
    /** Source file of the method's class; null for library classes. */
    val sourceFile: String? = null,
    /** First line of the method body, when known. */
    val lineNumber: Int? = null,
    /**
     * With `branches`: the branch labels (outside in) under which the previous step calls this one, one list
     * per call site; an empty list for an unconditional call. Null for the entry point, or when the previous
     * step has no Kotlin source.
     */
    val conditions: List<List<String>>? = null,
)

/** The paths found from one entry point to the target, shortest first. */
data class EntryPaths(
    val entry: MethodRef,
    val paths: List<List<PathStep>>,
    /** True when more paths exist than were listed. */
    val morePaths: Boolean,
)

data class CallPathResult(
    /** Number of entry points searched. */
    val entryCount: Int,
    /** Entry points with at least one (unguarded, when a guard is given) path to the target. */
    val reaching: List<EntryPaths>,
    /** Entry points that reach the target, but only through guarded methods. Empty without a guard. */
    val guardedEntries: List<MethodRef>,
    /** True when more entry points reach the target than were listed. */
    val truncated: Boolean,
)

/**
 * Finds call paths from entry points to target methods over the bytecode call graph.
 *
 * With a guard, a method counts as guarded when its qualified name or one of its (or its class's)
 * annotations matches the guard, or when it calls a guarded method within `guardDepth` calls. Guarded
 * methods are taken out of the graph, so the paths that remain are the ones that skip the guard. The
 * check ignores call order and conditions: calling the guard anywhere in the method counts.
 */
object CallPathFinder {

    private const val UNKNOWN_SOURCE = "<unknown>"

    fun find(
        graph: CallGraph,
        entries: List<MethodRef>,
        targets: Set<MethodRef>,
        maxDepth: Int,
        guard: Regex? = null,
        guardDepth: Int = 1,
        filter: ((MethodRef) -> Boolean)? = null,
        interfaceImplementors: Map<ClassName, Set<ClassName>> = emptyMap(),
        annotationsOf: (MethodRef) -> Set<AnnotationName> = { emptySet() },
        maxPathsPerEntry: Int = 3,
        maxEntries: Int = 50,
    ): CallPathResult {
        val search = Search(graph, targets, maxDepth, filter, interfaceImplementors)
        val guarded = guard?.let { GuardCheck(graph, it, guardDepth, annotationsOf) }

        val starts = entries.distinct().filter { it !in targets }
        val distAll = search.distancesToTarget(starts, blocked = { false })
        val distOpen = if (guarded == null) distAll else search.distancesToTarget(starts, blocked = guarded::isGuarded)

        val reaching = mutableListOf<EntryPaths>()
        val guardedEntries = mutableListOf<MethodRef>()
        var truncated = false
        for (entry in starts) {
            val reachesAtAll = (distAll[entry] ?: Int.MAX_VALUE) <= maxDepth
            val reachesOpen = (distOpen[entry] ?: Int.MAX_VALUE) <= maxDepth
            when {
                reachesOpen -> {
                    if (reaching.size == maxEntries) {
                        truncated = true
                    } else {
                        reaching += search.paths(entry, distOpen, maxPathsPerEntry)
                    }
                }
                reachesAtAll -> guardedEntries += entry
            }
        }
        return CallPathResult(entries.size, reaching, guardedEntries, truncated)
    }

    /**
     * Entry points by method-name [pattern], by method [annotation], or both (methods matching both).
     * Only method-level annotations count: a class annotation such as `@RestController` would make every
     * method of the class an entry point.
     */
    fun selectEntries(
        graph: CallGraph,
        pattern: String?,
        annotation: Regex?,
        methodAnnotations: Map<MethodRef, Set<AnnotationName>>,
        filter: ((MethodRef) -> Boolean)?,
    ): List<MethodRef> {
        require(pattern != null || annotation != null) { "Give an entry-point pattern, an annotation, or both" }
        val byPattern = pattern?.let { regex -> graph.findMethods(regex).filter(::isEntryCandidate) }
        val byAnnotation = annotation?.let { regex ->
            methodAnnotations.filterValues { names -> names.any { it.matches(regex) } }.keys.sortedBy { it.qualifiedName }
        }
        val selected = when {
            byPattern != null && byAnnotation != null -> byAnnotation.toSet().let { annotated -> byPattern.filter { it in annotated } }
            byPattern != null -> byPattern
            else -> byAnnotation!!
        }
        return if (filter == null) selected else selected.filter(filter)
    }

    /**
     * Constructors, static initializers and field pseudo-methods (<init>, <clinit>, <field:x>) are not entry
     * points, nor are methods of compiler-made anonymous classes such as a coroutine's `Routes$handle$1`: a
     * method pattern matches them by accident (regex `.` also matches `$`), and they repeat the real method.
     */
    private fun isEntryCandidate(method: MethodRef): Boolean =
        !method.methodName.startsWith("<") && !method.className.isSynthetic()

    /** Annotations on a method plus those on its class. */
    fun annotationLookup(annotations: AggregatedAnnotations): (MethodRef) -> Set<AnnotationName> = { method ->
        annotations.methodAnnotations[method].orEmpty() + annotations.classAnnotations[method.className].orEmpty()
    }

    private class GuardCheck(
        private val graph: CallGraph,
        private val guard: Regex,
        private val guardDepth: Int,
        private val annotationsOf: (MethodRef) -> Set<AnnotationName>,
    ) {
        private val memo = mutableMapOf<Pair<MethodRef, Int>, Boolean>()

        fun isGuarded(method: MethodRef): Boolean = guardedWithin(method, guardDepth)

        private fun guardedWithin(method: MethodRef, depth: Int): Boolean {
            val key = method to depth
            memo[key]?.let { return it }
            memo[key] = false // breaks recursion on cycles
            val result = matches(method) ||
                (depth > 0 && graph.calleesOf(method.className, method.methodName).any { guardedWithin(it, depth - 1) })
            memo[key] = result
            return result
        }

        private fun matches(method: MethodRef): Boolean =
            guard.containsMatchIn(method.qualifiedName) || annotationsOf(method).any { it.matches(guard) }
    }

    private class Search(
        private val graph: CallGraph,
        private val targets: Set<MethodRef>,
        private val maxDepth: Int,
        private val filter: ((MethodRef) -> Boolean)?,
        private val interfaceImplementors: Map<ClassName, Set<ClassName>>,
    ) {
        private val successorCache = mutableMapOf<MethodRef, List<PathStep>>()

        /** Callees (with interface calls expanded to implementors) that a path may step to. Targets are never filtered out. */
        fun successors(method: MethodRef): List<PathStep> = successorCache.getOrPut(method) {
            val steps = linkedMapOf<MethodRef, PathStep>()
            for (callee in graph.calleesOf(method.className, method.methodName).sortedBy { it.qualifiedName }) {
                if (allowed(callee)) steps.putIfAbsent(callee, step(callee))
                for (impl in interfaceImplementors[callee.className].orEmpty().sortedBy { it.value }) {
                    val implMethod = MethodRef(impl, callee.methodName)
                    if (allowed(implMethod)) steps.putIfAbsent(implMethod, step(implMethod, via = callee.className))
                }
            }
            steps.values.toList()
        }

        fun step(method: MethodRef, via: ClassName? = null): PathStep = PathStep(
            method,
            via,
            sourceFile = graph.sourceFileOf(method.className).takeIf { it != UNKNOWN_SOURCE },
            lineNumber = graph.lineNumberOf(method),
        )

        private fun allowed(method: MethodRef): Boolean =
            method in targets || (!isSyntheticRef(method) && (filter?.invoke(method) ?: true))

        /** Field reads and class literals are recorded as pseudo-methods in the call graph; they call nothing. */
        private fun isSyntheticRef(method: MethodRef): Boolean =
            method.methodName.startsWith("<field:") || method.methodName == "<class-literal>"

        /** Calls needed from each method to reach a target, for methods reachable from [starts] within maxDepth. */
        fun distancesToTarget(starts: List<MethodRef>, blocked: (MethodRef) -> Boolean): Map<MethodRef, Int> {
            val predecessors = mutableMapOf<MethodRef, MutableSet<MethodRef>>()
            val depthOf = mutableMapOf<MethodRef, Int>()
            val queue = ArrayDeque<MethodRef>()
            for (start in starts) if (!blocked(start) && depthOf.putIfAbsent(start, 0) == null) queue += start
            while (queue.isNotEmpty()) {
                val current = queue.removeFirst()
                val depth = depthOf.getValue(current)
                if (current in targets || depth >= maxDepth) continue
                for (step in successors(current)) {
                    if (blocked(step.method)) continue
                    predecessors.getOrPut(step.method) { mutableSetOf() } += current
                    if (depthOf.putIfAbsent(step.method, depth + 1) == null) queue += step.method
                }
            }
            val distance = mutableMapOf<MethodRef, Int>()
            val back = ArrayDeque<MethodRef>()
            for (target in targets) if (target in depthOf) { distance[target] = 0; back += target }
            while (back.isNotEmpty()) {
                val current = back.removeFirst()
                for (pred in predecessors[current].orEmpty()) {
                    if (pred in targets || pred in distance) continue
                    distance[pred] = distance.getValue(current) + 1
                    back += pred
                }
            }
            return distance
        }

        /** Simple paths from [entry] to a target within maxDepth, shortest first, at most [limit]. */
        fun paths(entry: MethodRef, distance: Map<MethodRef, Int>, limit: Int): EntryPaths {
            val found = mutableListOf<List<PathStep>>()
            var more = false
            val onPath = mutableSetOf(entry)
            val path = mutableListOf(step(entry))

            fun walk() {
                if (more) return
                val current = path.last().method
                if (current in targets) {
                    if (found.size == limit) more = true else found += path.toList()
                    return
                }
                val next = successors(current)
                    .filter { it.method !in onPath }
                    .filter { step -> distance[step.method]?.let { path.size + it <= maxDepth } ?: false }
                    .sortedBy { distance.getValue(it.method) }
                for (step in next) {
                    onPath += step.method
                    path += step
                    walk()
                    path.removeLast()
                    onPath -= step.method
                    if (more) return
                }
            }
            walk()
            return EntryPaths(entry, found.sortedBy { it.size }, more)
        }
    }
}
