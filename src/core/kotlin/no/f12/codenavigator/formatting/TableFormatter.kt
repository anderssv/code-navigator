package no.f12.codenavigator.formatting

import no.f12.codenavigator.navigation.classinfo.ClassInfo

object TableFormatter {
    fun format(classes: List<ClassInfo>): String {
        if (classes.isEmpty()) return "No classes found."

        val classHeader = "Class"
        val sourceHeader = "Source File"
        val classWidth = maxOf(classHeader.length, classes.maxOf { it.className.displayName().length })
        val sourceWidth = maxOf(sourceHeader.length, classes.maxOf { it.reconstructedSourcePath.length })

        return buildString {
            appendLine("${classHeader.padEnd(classWidth)} | $sourceHeader")
            appendLine("${"-".repeat(classWidth)} | ${"-".repeat(sourceWidth)}")
            for (entry in classes) {
                appendLine("${entry.className.displayName().padEnd(classWidth)} | ${entry.reconstructedSourcePath}")
            }
            append("\n${classes.size} classes found.")
        }
    }
}
