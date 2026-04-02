package no.f12.codenavigator.navigation.core

object PatternEnhancer {

    private val STOPWORDS = setOf("And", "Or", "Of", "The", "For", "In", "To", "By", "On", "With")

    private val REGEX_META = Regex("[.*+?|\\[\\](){}^$\\\\]")

    private val CAMEL_BOUNDARY = Regex("(?<=[a-z])(?=[A-Z])")

    fun enhance(pattern: String): String {
        if (REGEX_META.containsMatchIn(pattern) || pattern.contains('.')) return pattern

        val segments = pattern.split(CAMEL_BOUNDARY)
        if (segments.size <= 1) return pattern

        val result = StringBuilder(segments[0])
        var i = 1
        while (i < segments.size) {
            if (segments[i] in STOPWORDS) {
                result.append("(?:${segments[i]})?")
            } else {
                result.append(".*${segments[i]}")
            }
            i++
        }
        return result.toString()
    }
}
