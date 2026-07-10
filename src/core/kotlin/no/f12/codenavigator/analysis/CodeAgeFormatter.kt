package no.f12.codenavigator.analysis

import no.f12.codenavigator.formatting.jsonArray
import no.f12.codenavigator.formatting.jsonObject
import no.f12.codenavigator.formatting.withInterpretation

object CodeAgeFormatter {

    internal const val AGE_INTERPRETATION = "Interpretation: Old files (many months since last change) are either stable infrastructure or forgotten code. Very old files in active packages may indicate dead code or deferred maintenance."

    fun format(ages: List<FileAge>): String {
        if (ages.isEmpty()) return "No files found."

        val fileWidth = maxOf("File".length, ages.maxOf { it.file.length })
        val ageWidth = maxOf("Age (months)".length, ages.maxOf { it.ageMonths.toString().length })

        return buildString {
            appendLine("%-${fileWidth}s  %${ageWidth}s  Last Changed".format("File", "Age (months)"))
            ages.forEachIndexed { index, a ->
                if (index > 0) appendLine()
                append("%-${fileWidth}s  %${ageWidth}d  %s".format(a.file, a.ageMonths, a.lastChangeDate))
            }
        }
    }

    fun formatJson(ages: List<FileAge>): String =
        jsonArray(ages) { a ->
            jsonObject(
                "file" to a.file,
                "ageMonths" to a.ageMonths,
                "lastChangeDate" to a.lastChangeDate.toString(),
            )
        }

    fun formatLlm(ages: List<FileAge>): String =
        ages.joinToString("\n") { "${it.file} age=${it.ageMonths}months last=${it.lastChangeDate}" }
            .withInterpretation(AGE_INTERPRETATION)
}
