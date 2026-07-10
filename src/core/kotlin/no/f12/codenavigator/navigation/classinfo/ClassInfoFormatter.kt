package no.f12.codenavigator.navigation.classinfo

import no.f12.codenavigator.formatting.jsonArray
import no.f12.codenavigator.formatting.jsonObject

/** JSON/LLM formatting for [ClassInfo]. TEXT rendering uses the generic `TableFormatter` instead. */
object ClassInfoFormatter {

    fun formatJson(classes: List<ClassInfo>): String =
        jsonArray(classes.sortedBy { it.className }) { c ->
            jsonObject(
                "className" to c.className.displayName(),
                "sourceFile" to c.sourceFileName,
                "sourcePath" to c.reconstructedSourcePath,
            )
        }

    fun formatLlm(classes: List<ClassInfo>): String =
        classes.sortedBy { it.className }.joinToString("\n") { "${it.className.displayName()} ${it.sourceFileName}" }
}
