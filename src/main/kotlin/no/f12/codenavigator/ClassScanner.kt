package no.f12.codenavigator

import java.io.File

object ClassScanner {
    fun scan(classDirectories: List<File>): List<ClassInfo> =
        classDirectories
            .filter { it.exists() }
            .flatMap { dir ->
                dir.walkTopDown()
                    .filter { it.isFile && it.extension == "class" }
                    .map { ClassInfoExtractor.extract(it) }
                    .toList()
            }
            .filter { it.isUserDefinedClass }
            .sortedBy { it.className }
}
