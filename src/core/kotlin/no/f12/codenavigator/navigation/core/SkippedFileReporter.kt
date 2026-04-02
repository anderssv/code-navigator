package no.f12.codenavigator.navigation.core

import java.io.File

object SkippedFileReporter {

    fun report(skippedFiles: List<UnsupportedBytecodeVersionException>, reportFile: File): String? {
        if (skippedFiles.isEmpty()) return null

        reportFile.parentFile?.mkdirs()
        reportFile.writeText(skippedFiles.joinToString("\n") { it.message ?: it.toString() })

        return "Warning: ${skippedFiles.size} class file(s) were skipped (unsupported bytecode version). " +
            "See ${reportFile.absolutePath} for details."
    }
}
