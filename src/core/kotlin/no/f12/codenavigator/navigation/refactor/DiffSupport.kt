package no.f12.codenavigator.navigation.refactor

fun computeDiff(before: String, after: String): List<String> {
    val beforeLines = before.lines()
    val afterLines = after.lines()
    val diff = mutableListOf<String>()

    val maxLines = maxOf(beforeLines.size, afterLines.size)
    for (i in 0 until maxLines) {
        val bLine = beforeLines.getOrNull(i)
        val aLine = afterLines.getOrNull(i)
        when {
            bLine == aLine -> {}
            bLine != null && aLine != null -> {
                diff.add("- $bLine")
                diff.add("+ $aLine")
            }
            bLine != null -> diff.add("- $bLine")
            aLine != null -> diff.add("+ $aLine")
        }
    }
    return diff
}
