package no.f12.codenavigator.navigation

import java.io.File

data class AnnotationMatch(
    val className: ClassName,
    val sourceFile: String?,
    val classAnnotations: Set<String>,
    val matchedMethods: List<MethodAnnotationMatch>,
)

data class MethodAnnotationMatch(
    val method: MethodRef,
    val annotations: Set<String>,
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
                            val classMatches = scanResult.classAnnotations.any { regex.containsMatchIn(it) }
                            val matchingMethods = if (methods) {
                                scanResult.methodAnnotations
                                    .filter { (_, annotations) -> annotations.any { regex.containsMatchIn(it) } }
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
