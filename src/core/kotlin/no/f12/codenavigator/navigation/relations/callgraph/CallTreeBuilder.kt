package no.f12.codenavigator.navigation.relations.callgraph

import no.f12.codenavigator.navigation.types.AnnotationName
import no.f12.codenavigator.navigation.types.ClassName
import no.f12.codenavigator.navigation.types.SourceSet
import no.f12.codenavigator.navigation.types.FrameworkPresets

data class AnnotationTag(
    val name: AnnotationName,
    val framework: String? = null,
    val parameters: Map<String, String> = emptyMap(),
)

data class CallTreeNode(
    val method: MethodRef,
    val sourceFile: String?,
    val lineNumber: Int?,
    val children: List<CallTreeNode>,
    val annotations: List<AnnotationTag> = emptyList(),
    val sourceSet: SourceSet? = null,
    /**
     * Set on an interface method node (CALLEES direction only) when it has more polymorphic
     * implementors than [DEFAULT_MAX_IMPLEMENTORS]/`--max-implementors` — the count of
     * implementors NOT individually expanded as children, to avoid tree explosion on
     * widely-implemented interfaces (e.g. Repository, EventHandler).
     */
    val collapsedImplementorCount: Int = 0,
    /**
     * True when this node is a Kotlin `inline` function (or its `$$forInline` twin). The compiler
     * copies the body into every call site, so **no invoke instruction survives for bytecode
     * analysis to find** — a CALLERS result for an inline function is structurally incomplete, and
     * an empty one does not mean the function is unused. Carried as a flag rather than a rendered
     * string so every output format (including JSON) can expose it.
     */
    val isInline: Boolean = false,
)

object CallTreeBuilder {

    const val DEFAULT_MAX_IMPLEMENTORS = 5

    fun build(
        graph: CallGraph,
        roots: List<MethodRef>,
        maxDepth: Int,
        direction: CallDirection,
        filter: ((MethodRef) -> Boolean)? = null,
        interfaceImplementors: Map<ClassName, Set<ClassName>> = emptyMap(),
        classToInterfaces: Map<ClassName, Set<ClassName>> = emptyMap(),
        classAnnotations: Map<ClassName, Set<AnnotationName>> = emptyMap(),
        methodAnnotations: Map<MethodRef, Set<AnnotationName>> = emptyMap(),
        classAnnotationParameters: Map<ClassName, Map<AnnotationName, Map<String, String>>> = emptyMap(),
        methodAnnotationParameters: Map<MethodRef, Map<AnnotationName, Map<String, String>>> = emptyMap(),
        maxImplementors: Int = DEFAULT_MAX_IMPLEMENTORS,
        inlineMethods: Set<MethodRef> = emptySet(),
    ): List<CallTreeNode> {
        return roots.map { method ->
            buildNode(graph, method, maxDepth, direction, depth = 0, visited = mutableSetOf(), filter = filter, interfaceImplementors = interfaceImplementors, classToInterfaces = classToInterfaces, classAnnotations = classAnnotations, methodAnnotations = methodAnnotations, classAnnotationParameters = classAnnotationParameters, methodAnnotationParameters = methodAnnotationParameters, maxImplementors = maxImplementors, inlineMethods = inlineMethods)
        }
    }

    /**
     * Kotlin compiles an inline function twice: the callable copy and a `$$forInline` twin. Metadata
     * only names the former, so the twin has to be matched by name or it would be reported as a
     * plain function with no callers — the exact trap this flag exists to close.
     */
    internal fun isInlineMethod(method: MethodRef, inlineMethods: Set<MethodRef>): Boolean {
        if (method in inlineMethods) return true
        val base = method.methodName.removeSuffix(FOR_INLINE_SUFFIX)
        return base != method.methodName && MethodRef(method.className, base) in inlineMethods
    }

    private const val FOR_INLINE_SUFFIX = "\$\$forInline"

    private fun buildNode(
        graph: CallGraph,
        method: MethodRef,
        maxDepth: Int,
        direction: CallDirection,
        depth: Int,
        visited: MutableSet<MethodRef>,
        filter: ((MethodRef) -> Boolean)?,
        interfaceImplementors: Map<ClassName, Set<ClassName>>,
        classToInterfaces: Map<ClassName, Set<ClassName>>,
        classAnnotations: Map<ClassName, Set<AnnotationName>>,
        methodAnnotations: Map<MethodRef, Set<AnnotationName>>,
        classAnnotationParameters: Map<ClassName, Map<AnnotationName, Map<String, String>>>,
        methodAnnotationParameters: Map<MethodRef, Map<AnnotationName, Map<String, String>>>,
        maxImplementors: Int,
        inlineMethods: Set<MethodRef>,
    ): CallTreeNode {
        val sourceFile = graph.sourceFileOf(method.className)
        val lineNumber = graph.lineNumberOf(method)
        val annotations = resolveAnnotations(method, classAnnotations, methodAnnotations, classAnnotationParameters, methodAnnotationParameters)
        val depthCheck = depth < maxDepth
        val visitedCheck = method !in visited
        var children: List<CallTreeNode> = emptyList()
        var collapsedByChild: Map<MethodRef, Int> = emptyMap()
        if (depthCheck && visitedCheck) {
            visited.add(method)
            val direct = direction.resolve(graph, method.className, method.methodName)
                .let { refs -> if (filter != null) refs.filter(filter).toSet() else refs }

            val dispatchByCallee = if (direction == CallDirection.CALLEES) {
                resolveInterfaceDispatchByCallee(graph, method, interfaceImplementors)
            } else {
                emptyMap()
            }
            val (keptImplementors, collapsedCounts) = capImplementors(dispatchByCallee, filter, maxImplementors)
            collapsedByChild = collapsedCounts

            val dispatchedCallers = if (direction == CallDirection.CALLERS) {
                resolveInterfaceDispatchCallers(graph, method, classToInterfaces)
                    .let { refs -> if (filter != null) refs.filter(filter).toSet() else refs }
            } else {
                emptySet()
            }

            val related = direct + keptImplementors + dispatchedCallers
            children = related.sortedBy { it.qualifiedName }.map { child ->
                val node = buildNode(graph, child, maxDepth, direction, depth + 1, visited, filter, interfaceImplementors, classToInterfaces, classAnnotations, methodAnnotations, classAnnotationParameters, methodAnnotationParameters, maxImplementors, inlineMethods)
                val collapsed = collapsedByChild[child] ?: 0
                if (collapsed > 0) node.copy(collapsedImplementorCount = collapsed) else node
            }
        }
        return CallTreeNode(
            method,
            sourceFile,
            lineNumber,
            children,
            annotations,
            graph.sourceSetOf(method.className),
            isInline = isInlineMethod(method, inlineMethods),
        )
    }

    private fun resolveAnnotations(
        method: MethodRef,
        classAnnotations: Map<ClassName, Set<AnnotationName>>,
        methodAnnotations: Map<MethodRef, Set<AnnotationName>>,
        classAnnotationParameters: Map<ClassName, Map<AnnotationName, Map<String, String>>>,
        methodAnnotationParameters: Map<MethodRef, Map<AnnotationName, Map<String, String>>>,
    ): List<AnnotationTag> {
        val names = methodAnnotations[method]
            ?: classAnnotations[method.className]
            ?: return emptyList()
        val paramMap = methodAnnotationParameters[method]
            ?: classAnnotationParameters[method.className]
            ?: emptyMap()
        return names.sorted().map { name ->
            AnnotationTag(name, FrameworkPresets.frameworkOf(name), paramMap[name] ?: emptyMap())
        }
    }

    /** callee (e.g. Interface.method()) -> its polymorphic implementor MethodRefs. */
    private fun resolveInterfaceDispatchByCallee(
        graph: CallGraph,
        method: MethodRef,
        interfaceImplementors: Map<ClassName, Set<ClassName>>,
    ): Map<MethodRef, Set<MethodRef>> {
        if (interfaceImplementors.isEmpty()) return emptyMap()
        val direct = graph.calleesOf(method.className, method.methodName)
        return direct.mapNotNull { callee ->
            val impls = interfaceImplementors[callee.className] ?: return@mapNotNull null
            if (impls.isEmpty()) return@mapNotNull null
            callee to impls.map { implClass -> MethodRef(implClass, callee.methodName) }.toSet()
        }.toMap()
    }

    /** Caps each interface method's implementor set independently; returns (kept implementors, {calleeMethod: collapsedCount}). */
    private fun capImplementors(
        dispatchByCallee: Map<MethodRef, Set<MethodRef>>,
        filter: ((MethodRef) -> Boolean)?,
        maxImplementors: Int,
    ): Pair<Set<MethodRef>, Map<MethodRef, Int>> {
        if (dispatchByCallee.isEmpty()) return emptySet<MethodRef>() to emptyMap()

        val kept = mutableSetOf<MethodRef>()
        val collapsedCounts = mutableMapOf<MethodRef, Int>()
        for ((callee, impls) in dispatchByCallee) {
            val filtered = if (filter != null) impls.filter(filter) else impls.toList()
            val sorted = filtered.sortedBy { it.qualifiedName }
            kept += sorted.take(maxImplementors)
            val collapsed = sorted.size - maxImplementors
            if (collapsed > 0) collapsedCounts[callee] = collapsed
        }
        return kept to collapsedCounts
    }

    private fun resolveInterfaceDispatchCallers(
        graph: CallGraph,
        method: MethodRef,
        classToInterfaces: Map<ClassName, Set<ClassName>>,
    ): Set<MethodRef> {
        if (classToInterfaces.isEmpty()) return emptySet()
        // When looking for callers of Impl.method(), also find callers of Interface.method()
        val interfaces = classToInterfaces[method.className] ?: emptySet()
        return interfaces.flatMap { iface ->
            graph.callersOf(iface, method.methodName)
        }.toSet()
    }
}
