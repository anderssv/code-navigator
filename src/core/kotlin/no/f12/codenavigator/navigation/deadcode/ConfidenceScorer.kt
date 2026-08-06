package no.f12.codenavigator.navigation.deadcode

import no.f12.codenavigator.navigation.relations.callgraph.CallGraph
import no.f12.codenavigator.navigation.relations.callgraph.MethodRef
import no.f12.codenavigator.navigation.types.AnnotationName
import no.f12.codenavigator.navigation.types.ClassName

object ConfidenceScorer {

    fun score(
        className: ClassName,
        method: MethodRef?,
        testGraph: CallGraph?,
        referencedInTests: Boolean,
        classAnnotations: Map<ClassName, Set<AnnotationName>>,
        methodAnnotations: Map<MethodRef, Set<AnnotationName>>,
        classExternalInterfaces: Map<ClassName, Set<ClassName>>,
        modifierAnnotated: Set<String> = emptySet(),
        constValHolders: Set<ClassName> = emptySet(),
    ): DeadCodeConfidence {
        if (hasModifierAnnotation(className, method, classAnnotations, methodAnnotations, modifierAnnotated)) {
            return DeadCodeConfidence.LOW
        }

        // Kotlin inlines const val references as literal values at every call site,
        // so bytecode can never carry evidence that a const val holder is used —
        // absence of references cannot be treated as HIGH-confidence dead code here.
        if (method == null && className in constValHolders) {
            return DeadCodeConfidence.LOW
        }

        val hasClassAnnotations = classAnnotations.containsKey(className)
        val hasMethodAnnotations = method != null && methodAnnotations.containsKey(method)
        if (method == null && hasClassAnnotations) return DeadCodeConfidence.LOW
        if (hasMethodAnnotations) {
            return DeadCodeConfidence.LOW
        }

        if (method != null && classExternalInterfaces.containsKey(className)) {
            return DeadCodeConfidence.LOW
        }

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
}
