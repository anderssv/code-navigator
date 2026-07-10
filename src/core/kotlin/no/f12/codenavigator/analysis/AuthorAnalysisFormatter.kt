package no.f12.codenavigator.analysis

import no.f12.codenavigator.formatting.jsonArray
import no.f12.codenavigator.formatting.jsonObject

object AuthorAnalysisFormatter {

    fun format(modules: List<ModuleAuthors>): String {
        if (modules.isEmpty()) return "No files found."

        val fileWidth = maxOf("File".length, modules.maxOf { it.file.length })
        val authorsWidth = maxOf("Authors".length, modules.maxOf { it.authors.toString().length })
        val revsWidth = maxOf("Revisions".length, modules.maxOf { it.revisions.toString().length })

        return buildString {
            appendLine("%-${fileWidth}s  %${authorsWidth}s  %${revsWidth}s".format("File", "Authors", "Revisions"))
            modules.forEachIndexed { index, m ->
                if (index > 0) appendLine()
                append("%-${fileWidth}s  %${authorsWidth}d  %${revsWidth}d".format(m.file, m.authors, m.revisions))
            }
        }
    }

    fun formatJson(modules: List<ModuleAuthors>): String =
        jsonArray(modules) { m ->
            jsonObject(
                "file" to m.file,
                "authors" to m.authors,
                "revisions" to m.revisions,
            )
        }

    fun formatLlm(modules: List<ModuleAuthors>): String =
        modules.joinToString("\n") { "${it.file} authors=${it.authors} revisions=${it.revisions}" }
}
