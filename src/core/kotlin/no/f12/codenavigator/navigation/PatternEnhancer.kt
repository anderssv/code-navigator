package no.f12.codenavigator.navigation

object PatternEnhancer {
    fun enhance(pattern: String): String =
        pattern.replace(Regex("(?<=[a-z])(?=[A-Z])"), ".*")
}
