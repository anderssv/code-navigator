package no.f12.codenavigator.navigation.annotation

import no.f12.codenavigator.navigation.types.AnnotationName
import no.f12.codenavigator.navigation.types.ClassName
import no.f12.codenavigator.navigation.relations.callgraph.MethodRef
import no.f12.codenavigator.navigation.bytecode.UnsupportedBytecodeVersionException
import java.io.File

data class AnnotationMatch(
    val className: ClassName,
    val sourceFile: String?,
    val classAnnotations: Set<AnnotationName>,
    val matchedMethods: List<MethodAnnotationMatch>,
    val matchedFields: List<FieldAnnotationMatch> = emptyList(),
)

data class MethodAnnotationMatch(
    val method: MethodRef,
    val annotations: Set<AnnotationName>,
)

data class FieldAnnotationMatch(
    val field: FieldRef,
    val annotations: Set<AnnotationName>,
)

object AnnotationQueryBuilder {

    fun query(
        classDirectories: List<File>,
        pattern: String,
        targets: Set<AnnotationTarget> = AnnotationTarget.ALL,
    ): List<AnnotationMatch> {
        val regex = Regex(pattern, RegexOption.IGNORE_CASE)
        val results = mutableListOf<AnnotationMatch>()

        classDirectories
            .filter { it.exists() }
            .forEach { dir ->
                dir.walkTopDown()
                    .filter { it.isFile && it.extension == "class" }
                    .forEach { classFile ->
                        try {
                            val scanResult = AnnotationExtractor.extract(classFile)
                            val classMatches = AnnotationTarget.CLASS in targets &&
                                scanResult.classAnnotations.any { it.matches(regex) }
                            val matchingMethods = if (AnnotationTarget.METHOD in targets) {
                                scanResult.methodAnnotations
                                    .filter { (_, annotations) -> annotations.any { it.matches(regex) } }
                                    .map { (methodRef, annotations) -> MethodAnnotationMatch(methodRef, annotations) }
                                    .sortedBy { it.method.methodName }
                            } else {
                                emptyList()
                            }
                            val matchingFields = if (AnnotationTarget.FIELD in targets) {
                                scanResult.fieldAnnotations
                                    .filter { (_, annotations) -> annotations.any { it.matches(regex) } }
                                    .map { (fieldRef, annotations) -> FieldAnnotationMatch(fieldRef, annotations) }
                                    .sortedBy { it.field.fieldName }
                            } else {
                                emptyList()
                            }

                            if (classMatches || matchingMethods.isNotEmpty() || matchingFields.isNotEmpty()) {
                                results.add(
                                    AnnotationMatch(
                                        className = scanResult.className,
                                        sourceFile = scanResult.sourceFile,
                                        classAnnotations = scanResult.classAnnotations,
                                        matchedMethods = matchingMethods,
                                        matchedFields = matchingFields,
                                    ),
                                )
                            }
                        } catch (_: UnsupportedBytecodeVersionException) {
                            // skip
                        }
                    }
            }

        return results.sortedBy { it.className.value }
    }
}
