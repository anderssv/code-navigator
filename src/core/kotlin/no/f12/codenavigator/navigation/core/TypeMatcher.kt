package no.f12.codenavigator.navigation.core

/**
 * Encapsulates how a user-supplied pattern is matched against class names
 * found in bytecode. Separates pattern resolution from scanning.
 */
sealed interface TypeMatcher {
    fun matches(className: ClassName): Boolean

    /** Matches class names using regex containsMatchIn. */
    data class RegexMatcher(val regex: Regex) : TypeMatcher {
        override fun matches(className: ClassName): Boolean =
            regex.containsMatchIn(className.value)
    }

    /** Matches only the exact class and its inner classes ($-nested). */
    data class ExactMatcher(val target: ClassName) : TypeMatcher {
        override fun matches(className: ClassName): Boolean =
            className.value == target.value ||
                className.value.startsWith("${target.value}$")
    }

    companion object {
        /**
         * Builds the appropriate matcher for a pattern string.
         * FQN patterns (dots, no metacharacters) produce an ExactMatcher.
         * Everything else produces a RegexMatcher.
         */
        fun fromPattern(pattern: String): TypeMatcher =
            if (PatternEnhancer.looksLikeFqn(pattern)) {
                ExactMatcher(ClassName(pattern))
            } else {
                RegexMatcher(Regex(pattern, RegexOption.IGNORE_CASE))
            }
    }
}
