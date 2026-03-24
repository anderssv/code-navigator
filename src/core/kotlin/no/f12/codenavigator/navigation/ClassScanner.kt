package no.f12.codenavigator.navigation

import java.io.File

object ClassScanner {
    fun scan(classDirectories: List<File>): ScanResult<List<ClassInfo>> {
        val classes = mutableListOf<ClassInfo>()
        val skipped = mutableListOf<UnsupportedBytecodeVersionException>()

        classDirectories
            .filter { it.exists() }
            .forEach { dir ->
                dir.walkTopDown()
                    .filter { it.isFile && it.extension == "class" }
                    .forEach { classFile ->
                        try {
                            val info = ClassInfoExtractor.extract(classFile)
                            if (info.isUserDefinedClass) {
                                classes.add(info)
                            }
                        } catch (e: UnsupportedBytecodeVersionException) {
                            skipped.add(e)
                        }
                    }
            }

        return ScanResult(
            data = classes.sortedBy { it.className },
            skippedFiles = skipped,
        )
    }
}
