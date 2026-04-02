package no.f12.codenavigator.navigation.stringconstant

import no.f12.codenavigator.navigation.core.ClassName
import no.f12.codenavigator.navigation.core.ScanResult
import no.f12.codenavigator.navigation.core.UnsupportedBytecodeVersionException
import java.io.File

object StringConstantScanner {

    fun scan(classDirectories: List<File>, pattern: Regex): ScanResult<List<StringConstantMatch>> {
        val matches = mutableListOf<StringConstantMatch>()
        val skipped = mutableListOf<UnsupportedBytecodeVersionException>()

        classDirectories
            .filter { it.exists() }
            .forEach { dir ->
                dir.walkTopDown()
                    .filter { it.isFile && it.extension == "class" }
                    .filter { !ClassName.isSyntheticName(it.nameWithoutExtension) }
                    .forEach { classFile ->
                        try {
                            StringConstantExtractor.extract(classFile)
                                .filter { pattern.containsMatchIn(it.value) }
                                .let { matches.addAll(it) }
                        } catch (e: UnsupportedBytecodeVersionException) {
                            skipped.add(e)
                        }
                    }
            }

        return ScanResult(
            data = matches.sortedWith(compareBy({ it.className.value }, { it.methodName }, { it.value })),
            skippedFiles = skipped,
        )
    }
}
