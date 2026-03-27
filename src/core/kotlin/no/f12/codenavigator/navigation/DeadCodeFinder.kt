package no.f12.codenavigator.navigation

enum class DeadCodeKind {
    CLASS,
    METHOD,
}

enum class DeadCodeConfidence {
    HIGH,
    MEDIUM,
    LOW,
}

data class DeadCode(
    val className: ClassName,
    val memberName: String?,
    val kind: DeadCodeKind,
    val sourceFile: String,
    val confidence: DeadCodeConfidence,
)

object DeadCodeFinder {

    fun find(
        graph: CallGraph,
        filter: Regex?,
        exclude: Regex?,
        classesOnly: Boolean,
        excludeAnnotated: Set<String>,
        classAnnotations: Map<ClassName, Set<String>>,
        methodAnnotations: Map<MethodRef, Set<String>>,
        testGraph: CallGraph?,
    ): List<DeadCode> {
        val projectClasses = graph.projectClasses()
        if (projectClasses.isEmpty()) return emptyList()

        val calledTypes = mutableSetOf<ClassName>()
        val calledMethods = mutableSetOf<MethodRef>()
        val projectMethods = mutableSetOf<MethodRef>()

        graph.forEachEdge { caller, callee ->
            if (caller.className in projectClasses) {
                projectMethods.add(caller)
            }
            if (callee.className in projectClasses) {
                projectMethods.add(callee)
            }
            if (caller.className != callee.className && callee.className in projectClasses) {
                calledTypes.add(callee.className)
                calledMethods.add(callee)
            }
        }

        val testCalledTypes = mutableSetOf<ClassName>()
        val testCalledMethods = mutableSetOf<MethodRef>()
        if (testGraph != null) {
            testGraph.forEachEdge { caller, callee ->
                if (callee.className in projectClasses && caller.className != callee.className) {
                    testCalledTypes.add(callee.className)
                    testCalledMethods.add(callee)
                }
            }
        }

        val results = mutableListOf<DeadCode>()

        for (cls in projectClasses) {
            if (cls !in calledTypes && !cls.isGenerated()) {
                results.add(
                    DeadCode(
                        className = cls,
                        memberName = null,
                        kind = DeadCodeKind.CLASS,
                        sourceFile = graph.sourceFileOf(cls),
                        confidence = classConfidence(cls, null, testGraph, cls in testCalledTypes, classAnnotations, methodAnnotations),
                    )
                )
            }
        }

        if (!classesOnly) {
            for (method in projectMethods) {
                if (method.className in calledTypes &&
                    method !in calledMethods &&
                    !method.className.isGenerated() &&
                    !method.isGenerated()
                ) {
                    results.add(
                        DeadCode(
                            className = method.className,
                            memberName = method.methodName,
                            kind = DeadCodeKind.METHOD,
                            sourceFile = graph.sourceFileOf(method.className),
                            confidence = classConfidence(method.className, method, testGraph, method in testCalledMethods, classAnnotations, methodAnnotations),
                        )
                    )
                }
            }
        }

        return results
            .filter { item -> filter == null || item.className.matches(filter) }
            .filter { item -> exclude == null || !item.className.matches(exclude) }
            .filter { item -> !isExcludedByAnnotation(item, excludeAnnotated, classAnnotations, methodAnnotations) }
            .sortedWith(compareBy({ it.kind }, { it.className }, { it.memberName ?: "" }))
    }

    private fun classConfidence(
        className: ClassName,
        method: MethodRef?,
        testGraph: CallGraph?,
        referencedInTests: Boolean,
        classAnnotations: Map<ClassName, Set<String>>,
        methodAnnotations: Map<MethodRef, Set<String>>,
    ): DeadCodeConfidence {
        val hasClassAnnotations = classAnnotations.containsKey(className)
        val hasMethodAnnotations = method != null && methodAnnotations.containsKey(method)
        if (hasClassAnnotations || hasMethodAnnotations) return DeadCodeConfidence.LOW

        if (testGraph != null && referencedInTests) return DeadCodeConfidence.MEDIUM

        return DeadCodeConfidence.HIGH
    }

    private fun isExcludedByAnnotation(
        item: DeadCode,
        excludeAnnotated: Set<String>,
        classAnnotations: Map<ClassName, Set<String>>,
        methodAnnotations: Map<MethodRef, Set<String>>,
    ): Boolean {
        if (excludeAnnotated.isEmpty()) return false
        val classAnns = classAnnotations[item.className] ?: emptySet()
        if (classAnns.any { it in excludeAnnotated }) return true
        if (item.kind == DeadCodeKind.METHOD && item.memberName != null) {
            val methodRef = MethodRef(item.className, item.memberName)
            val methodAnns = methodAnnotations[methodRef] ?: emptySet()
            if (methodAnns.any { it in excludeAnnotated }) return true
        }
        return false
    }
}
