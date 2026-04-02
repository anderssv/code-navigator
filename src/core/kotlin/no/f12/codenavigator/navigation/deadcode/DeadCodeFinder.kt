package no.f12.codenavigator.navigation.deadcode

import no.f12.codenavigator.navigation.core.AnnotationName
import no.f12.codenavigator.navigation.callgraph.CallGraph
import no.f12.codenavigator.navigation.core.ClassName
import no.f12.codenavigator.navigation.core.KotlinMethodFilter
import no.f12.codenavigator.navigation.callgraph.MethodRef

enum class DeadCodeKind {
    CLASS,
    METHOD,
}

enum class DeadCodeConfidence {
    HIGH,
    MEDIUM,
    LOW,
}

enum class DeadCodeReason {
    /** Not referenced in production or test code — highest removal confidence. */
    NO_REFERENCES,
    /** Referenced in test code but not in production — needs human judgment. */
    TEST_ONLY,
}

data class DeadCode(
    val className: ClassName,
    val memberName: String?,
    val kind: DeadCodeKind,
    val sourceFile: String,
    val confidence: DeadCodeConfidence,
    val reason: DeadCodeReason,
)

object DeadCodeFinder {

    fun find(
        graph: CallGraph,
        filter: Regex?,
        exclude: Regex?,
        classesOnly: Boolean,
        excludeAnnotated: Set<String>,
        classAnnotations: Map<ClassName, Set<AnnotationName>>,
        methodAnnotations: Map<MethodRef, Set<AnnotationName>>,
        testGraph: CallGraph?,
        interfaceImplementors: Map<ClassName, Set<ClassName>> = emptyMap(),
        classFields: Map<ClassName, Set<String>> = emptyMap(),
        inlineMethods: Set<MethodRef> = emptySet(),
        classExternalInterfaces: Map<ClassName, Set<ClassName>> = emptyMap(),
        prodOnly: Boolean = false,
        modifierAnnotated: Set<String> = emptySet(),
        supertypeEntryPoints: Set<ClassName> = emptySet(),
        testClasses: Set<ClassName> = emptySet(),
        classReceiverTypes: Map<ClassName, Set<ClassName>> = emptyMap(),
        receiverTypeEntryPoints: Set<ClassName> = emptySet(),
        testOnly: Boolean = false,
        delegationMethods: Set<MethodRef> = emptySet(),
        bridgeMethods: Set<MethodRef> = emptySet(),
    ): List<DeadCode> {
        val projectClasses = graph.projectClasses()
        if (projectClasses.isEmpty()) return emptyList()

        val calledTypes = mutableSetOf<ClassName>()
        val calledMethods = mutableSetOf<MethodRef>()
        val projectMethods = mutableSetOf<MethodRef>()

        val intraClassEdges = mutableMapOf<MethodRef, MutableSet<MethodRef>>()

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
            if (caller.className == callee.className && callee.className in projectClasses) {
                intraClassEdges.getOrPut(caller) { mutableSetOf() }.add(callee)
            }
        }

        // Propagate outer class liveness: if an inner class is alive,
        // its enclosing classes should be too (e.g. TokenError$ExitException → TokenError)
        for (cls in calledTypes.toList()) {
            var outer = cls.outerClass()
            while (outer != cls && outer.value.isNotEmpty() && outer in projectClasses) {
                calledTypes.add(outer)
                val next = outer.outerClass()
                if (next == outer) break
                outer = next
            }
        }

        // Unified propagation: resolve interface dispatch AND intra-class calls
        // in a single BFS. When a method becomes alive, we:
        //   1. Dispatch it to all implementors (interface/abstract resolution)
        //   2. Follow intra-class call edges to transitively mark callees alive
        // This handles the case where an intra-class call discovers a method
        // that needs dispatch (e.g. LeafPattern.match -> this.singleMatch ->
        // Argument.singleMatch / Command.singleMatch / Option.singleMatch).
        val queue = ArrayDeque(calledMethods.filter { it in projectMethods })
        while (queue.isNotEmpty()) {
            val method = queue.removeFirst()

            val implementors = interfaceImplementors[method.className]
            if (implementors != null) {
                for (implClass in implementors) {
                    if (implClass in projectClasses) {
                        calledTypes.add(implClass)
                        val dispatched = MethodRef(implClass, method.methodName)
                        if (dispatched !in calledMethods) {
                            calledMethods.add(dispatched)
                            queue.add(dispatched)
                        }
                    }
                }
            }

            val intraCallees = intraClassEdges[method] ?: continue
            for (callee in intraCallees) {
                if (callee !in calledMethods) {
                    calledMethods.add(callee)
                    queue.add(callee)
                }
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
            if (cls !in calledTypes && !cls.isGenerated() && !cls.isPackageInfo()) {
                val referencedInTests = cls in testCalledTypes
                val reason = if (testGraph != null && referencedInTests) DeadCodeReason.TEST_ONLY else DeadCodeReason.NO_REFERENCES
                results.add(
                    DeadCode(
                        className = cls,
                        memberName = null,
                        kind = DeadCodeKind.CLASS,
                        sourceFile = graph.sourceFileOf(cls),
                        confidence = classConfidence(cls, null, testGraph, referencedInTests, classAnnotations, methodAnnotations, classExternalInterfaces, modifierAnnotated),
                        reason = reason,
                    )
                )
            }
        }

        if (!classesOnly) {
            for (method in projectMethods) {
                if (method.className in calledTypes &&
                    method !in calledMethods &&
                    !method.className.isGenerated() &&
                    !method.isGenerated() &&
                    !isPropertyAccessor(method, classFields) &&
                    method !in inlineMethods &&
                    method !in delegationMethods &&
                    method !in bridgeMethods
                ) {
                    val referencedInTests = method in testCalledMethods
                    val reason = if (testGraph != null && referencedInTests) DeadCodeReason.TEST_ONLY else DeadCodeReason.NO_REFERENCES
                    results.add(
                        DeadCode(
                            className = method.className,
                            memberName = method.methodName,
                            kind = DeadCodeKind.METHOD,
                            sourceFile = graph.sourceFileOf(method.className),
                            confidence = classConfidence(method.className, method, testGraph, referencedInTests, classAnnotations, methodAnnotations, classExternalInterfaces, modifierAnnotated),
                            reason = reason,
                        )
                    )
                }
            }
        }

        return results
            .filter { item -> filter == null || item.className.matches(filter) }
            .filter { item -> exclude == null || !item.className.matches(exclude) }
            .filter { item -> !isExcludedByAnnotation(item, excludeAnnotated, classAnnotations, methodAnnotations) }
            .filter { item -> !isExcludedBySupertype(item, classExternalInterfaces, supertypeEntryPoints) }
            .filter { item -> !isExcludedByReceiverType(item, classReceiverTypes, receiverTypeEntryPoints) }
            .filter { item -> !prodOnly || (item.reason == DeadCodeReason.NO_REFERENCES && item.className !in testClasses) }
            .filter { item -> !testOnly || item.reason == DeadCodeReason.TEST_ONLY }
            .sortedWith(compareBy({ it.kind }, { it.className }, { it.memberName ?: "" }))
    }

    private fun classConfidence(
        className: ClassName,
        method: MethodRef?,
        testGraph: CallGraph?,
        referencedInTests: Boolean,
        classAnnotations: Map<ClassName, Set<AnnotationName>>,
        methodAnnotations: Map<MethodRef, Set<AnnotationName>>,
        classExternalInterfaces: Map<ClassName, Set<ClassName>>,
        modifierAnnotated: Set<String> = emptySet(),
    ): DeadCodeConfidence {
        if (hasModifierAnnotation(className, method, classAnnotations, methodAnnotations, modifierAnnotated)) {
            return DeadCodeConfidence.LOW
        }

        val hasClassAnnotations = classAnnotations.containsKey(className)
        val hasMethodAnnotations = method != null && methodAnnotations.containsKey(method)
        if (hasClassAnnotations || hasMethodAnnotations) return DeadCodeConfidence.LOW

        if (method != null && classExternalInterfaces.containsKey(className)) return DeadCodeConfidence.LOW

        if (testGraph != null && referencedInTests) return DeadCodeConfidence.MEDIUM

        return DeadCodeConfidence.HIGH
    }

    private fun hasModifierAnnotation(
        className: ClassName,
        method: MethodRef?,
        classAnnotations: Map<ClassName, Set<AnnotationName>>,
        methodAnnotations: Map<MethodRef, Set<AnnotationName>>,
        modifierAnnotated: Set<String>,
    ): Boolean {
        if (modifierAnnotated.isEmpty()) return false
        val classAnns = classAnnotations[className] ?: emptySet()
        if (classAnns.any { it.value in modifierAnnotated || it.simpleName() in modifierAnnotated }) return true
        if (method != null) {
            val methodAnns = methodAnnotations[method] ?: emptySet()
            if (methodAnns.any { it.value in modifierAnnotated || it.simpleName() in modifierAnnotated }) return true
        }
        return false
    }

    private fun isPropertyAccessor(
        method: MethodRef,
        classFields: Map<ClassName, Set<String>>,
    ): Boolean {
        val fields = classFields[method.className] ?: return false
        return KotlinMethodFilter.isAccessorForField(method.methodName, fields)
    }

    private fun isExcludedByAnnotation(
        item: DeadCode,
        excludeAnnotated: Set<String>,
        classAnnotations: Map<ClassName, Set<AnnotationName>>,
        methodAnnotations: Map<MethodRef, Set<AnnotationName>>,
    ): Boolean {
        if (excludeAnnotated.isEmpty()) return false
        val classAnns = classAnnotations[item.className] ?: emptySet()
        if (classAnns.any { it.value in excludeAnnotated || it.simpleName() in excludeAnnotated }) return true
        if (item.kind == DeadCodeKind.METHOD && item.memberName != null) {
            val methodRef = MethodRef(item.className, item.memberName)
            val methodAnns = methodAnnotations[methodRef] ?: emptySet()
            if (methodAnns.any { it.value in excludeAnnotated || it.simpleName() in excludeAnnotated }) return true
        }
        return false
    }

    private fun isExcludedBySupertype(
        item: DeadCode,
        classExternalInterfaces: Map<ClassName, Set<ClassName>>,
        supertypeEntryPoints: Set<ClassName>,
    ): Boolean {
        if (supertypeEntryPoints.isEmpty()) return false
        val externalInterfaces = classExternalInterfaces[item.className] ?: return false
        return externalInterfaces.any { it in supertypeEntryPoints }
    }

    private fun isExcludedByReceiverType(
        item: DeadCode,
        classReceiverTypes: Map<ClassName, Set<ClassName>>,
        receiverTypeEntryPoints: Set<ClassName>,
    ): Boolean {
        if (receiverTypeEntryPoints.isEmpty()) return false
        if (!item.className.value.endsWith("Kt")) return false
        val receiverTypes = classReceiverTypes[item.className] ?: return false
        return receiverTypes.any { it in receiverTypeEntryPoints }
    }
}
