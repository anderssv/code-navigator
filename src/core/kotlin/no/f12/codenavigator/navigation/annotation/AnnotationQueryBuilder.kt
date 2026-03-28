package no.f12.codenavigator.navigation.annotation

import no.f12.codenavigator.navigation.AnnotationName
import no.f12.codenavigator.navigation.ClassName
import no.f12.codenavigator.navigation.MethodRef
import no.f12.codenavigator.navigation.UnsupportedBytecodeVersionException
import java.io.File

data class AnnotationMatch(
    val className: ClassName,
    val sourceFile: String?,
    val classAnnotations: Set<AnnotationName>,
    val matchedMethods: List<MethodAnnotationMatch>,
)

data class MethodAnnotationMatch(
    val method: MethodRef,
    val annotations: Set<AnnotationName>,
)

object AnnotationQueryBuilder {

    fun query(
        classDirectories: List<File>,
        pattern: String,
        methods: Boolean,
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
                            val classMatches = scanResult.classAnnotations.any { it.matches(regex) }
                            val matchingMethods = if (methods) {
                                scanResult.methodAnnotations
                                    .filter { (_, annotations) -> annotations.any { it.matches(regex) } }
                                    .map { (methodRef, annotations) -> MethodAnnotationMatch(methodRef, annotations) }
                                    .sortedBy { it.method.methodName }
                            } else {
                                emptyList()
                            }

                            if (classMatches || matchingMethods.isNotEmpty()) {
                                results.add(
                                    AnnotationMatch(
                                        className = scanResult.className,
                                        sourceFile = scanResult.sourceFile,
                                        classAnnotations = scanResult.classAnnotations,
                                        matchedMethods = matchingMethods,
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
