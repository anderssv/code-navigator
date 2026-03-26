package no.f12.codenavigator.navigation

object RankFormatter {

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
}
