package no.f12.codenavigator.navigation.rank

import no.f12.codenavigator.formatting.jsonArray
import no.f12.codenavigator.formatting.jsonObject
import no.f12.codenavigator.formatting.withInterpretation

object RankFormatter {

    internal const val RANK_INTERPRETATION = "Interpretation: PageRank identifies structurally central classes. High-rank classes are depended on transitively by many others — changes to them have wide impact. Low-rank classes are peripheral and safer to modify."

    fun format(ranked: List<RankedType>): String {
        if (ranked.isEmpty()) return "No ranked types found."

        val classWidth = maxOf("Class".length, ranked.maxOf { it.className.toString().length })
        val rankWidth = maxOf("Rank".length, ranked.maxOf { "%.4f".format(it.rank).length })
        val inWidth = maxOf("In".length, ranked.maxOf { it.inDegree.toString().length })
        val outWidth = maxOf("Out".length, ranked.maxOf { it.outDegree.toString().length })

        return buildString {
            appendLine("%-${classWidth}s  %${rankWidth}s  %${inWidth}s  %${outWidth}s".format("Class", "Rank", "In", "Out"))
            ranked.forEachIndexed { index, r ->
                if (index > 0) appendLine()
                append("%-${classWidth}s  %${rankWidth}s  %${inWidth}d  %${outWidth}d".format(
                    r.className.toString(), "%.4f".format(r.rank), r.inDegree, r.outDegree
                ))
            }
        }
    }

    fun formatJson(ranked: List<RankedType>): String =
        jsonArray(ranked) { r ->
            jsonObject(
                "className" to r.className.toString(),
                "rank" to r.rank,
                "inDegree" to r.inDegree,
                "outDegree" to r.outDegree,
            )
        }

    fun formatLlm(ranked: List<RankedType>): String =
        ranked.joinToString("\n") { "%.4f".format(it.rank).let { rank -> "${it.className} rank=$rank in=${it.inDegree} out=${it.outDegree}" } }
            .withInterpretation(RANK_INTERPRETATION)
}
