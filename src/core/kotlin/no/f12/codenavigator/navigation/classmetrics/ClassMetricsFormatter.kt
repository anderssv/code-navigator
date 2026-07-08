package no.f12.codenavigator.navigation.classmetrics

object ClassMetricsFormatter {

    fun format(results: List<ClassMetricsResult>): String {
        if (results.isEmpty()) return "No matching classes found."

        val header = String.format("%-50s %6s %6s %-8s %5s %5s %5s", "Class", "TCC", "LCC", "Verdict", "WMC", "CBO", "DIT")
        val separator = "-".repeat(header.length)
        val rows = results.joinToString("\n") { r ->
            String.format(
                "%-50s %6.2f %6.2f %-8s %5d %5d %5d",
                r.className, r.tcc, r.lcc, r.verdict, r.wmc, r.cbo, r.dit,
            )
        }

        return "$header\n$separator\n$rows"
    }
}
