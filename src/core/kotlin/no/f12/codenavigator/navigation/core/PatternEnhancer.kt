package no.f12.codenavigator.navigation.core

object PatternEnhancer {

    private val STOPWORDS = setOf("And", "Or", "Of", "The", "For", "In", "To", "By", "On", "With")

    private val REGEX_META = Regex("[.*+?|\\[\\](){}^$\\\\]")

    /** Regex metacharacters excluding dot — dot is valid in FQN patterns. */
    private val REGEX_META_NO_DOT = Regex("[*+?|\\[\\](){}^$\\\\]")

    private val CAMEL_BOUNDARY = Regex("(?<=[a-z])(?=[A-Z])")

    /**
     * Returns true when the pattern looks like a fully-qualified class name
     * (contains dots, no regex metacharacters).
     */
    fun looksLikeFqn(pattern: String): Boolean =
        pattern.contains('.') && !REGEX_META_NO_DOT.containsMatchIn(pattern)

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
