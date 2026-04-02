package no.f12.codenavigator.navigation.callgraph

import no.f12.codenavigator.navigation.core.AnnotationName
import no.f12.codenavigator.navigation.core.ClassName
import no.f12.codenavigator.navigation.core.SourceSet
import no.f12.codenavigator.navigation.annotation.FrameworkPresets

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
)

object CallTreeBuilder {

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
    ): List<CallTreeNode> {
        return roots.map { method ->
            buildNode(graph, method, maxDepth, direction, depth = 0, visited = mutableSetOf(), filter = filter, interfaceImplementors = interfaceImplementors, classToInterfaces = classToInterfaces, classAnnotations = classAnnotations, methodAnnotations = methodAnnotations, classAnnotationParameters = classAnnotationParameters, methodAnnotationParameters = methodAnnotationParameters)
        }
    }

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
    ): CallTreeNode {
        val sourceFile = graph.sourceFileOf(method.className)
        val lineNumber = graph.lineNumberOf(method)
        val annotations = resolveAnnotations(method, classAnnotations, methodAnnotations, classAnnotationParameters, methodAnnotationParameters)
        val depthCheck = depth < maxDepth
        val visitedCheck = method !in visited
        val children = if (depthCheck && visitedCheck) {
            visited.add(method)
            val direct = direction.resolve(graph, method.className, method.methodName)
            val dispatched = resolveInterfaceDispatch(graph, method, direction, interfaceImplementors, classToInterfaces)
            val related = (direct + dispatched)
                .let { refs -> if (filter != null) refs.filter(filter).toSet() else refs }
            related.sortedBy { it.qualifiedName }.map { child ->
                buildNode(graph, child, maxDepth, direction, depth + 1, visited, filter, interfaceImplementors, classToInterfaces, classAnnotations, methodAnnotations, classAnnotationParameters, methodAnnotationParameters)
            }
        } else {
            emptyList()
        }
        return CallTreeNode(method, sourceFile, lineNumber, children, annotations, graph.sourceSetOf(method.className))
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

    private fun resolveInterfaceDispatch(
        graph: CallGraph,
        method: MethodRef,
        direction: CallDirection,
        interfaceImplementors: Map<ClassName, Set<ClassName>>,
        classToInterfaces: Map<ClassName, Set<ClassName>>,
    ): Set<MethodRef> {
        if (interfaceImplementors.isEmpty() && classToInterfaces.isEmpty()) return emptySet()

        return when (direction) {
            CallDirection.CALLERS -> {
                // When looking for callers of Impl.method(), also find callers of Interface.method()
                val interfaces = classToInterfaces[method.className] ?: emptySet()
                interfaces.flatMap { iface ->
                    graph.callersOf(iface, method.methodName)
                }.toSet()
            }
            CallDirection.CALLEES -> {
                // When a callee is Interface.method(), also show Impl.method() for all implementors
                val direct = graph.calleesOf(method.className, method.methodName)
                direct.flatMap { callee ->
                    val impls = interfaceImplementors[callee.className] ?: emptySet()
                    impls.map { implClass -> MethodRef(implClass, callee.methodName) }
                }.toSet()
            }
        }
    }
}
