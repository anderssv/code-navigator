package no.f12.codenavigator.navigation.classinfo

import no.f12.codenavigator.navigation.core.ScanResult
import no.f12.codenavigator.navigation.core.UnsupportedBytecodeVersionException
import java.io.File

object ClassDetailScanner {

    fun scan(classDirectories: List<File>, pattern: String): ScanResult<List<ClassDetail>> {
        val regex = Regex(pattern, RegexOption.IGNORE_CASE)
        val details = mutableListOf<ClassDetail>()
        val skipped = mutableListOf<UnsupportedBytecodeVersionException>()

        classDirectories
            .filter { it.exists() }
            .forEach { dir ->
                dir.walkTopDown()
                    .filter { it.isFile && it.extension == "class" }
                    .forEach { classFile ->
                        try {
                            val info = ClassInfoExtractor.extract(classFile)
                            if (info.isUserDefinedClass && info.className.matches(regex)) {
                                details.add(ClassDetailExtractor.extract(classFile))
                            }
                        } catch (e: UnsupportedBytecodeVersionException) {
                            skipped.add(e)
                        }
                    }
            }

        return ScanResult(
            data = details.sortedBy { it.className },
            skippedFiles = skipped,
        )
    }
}
