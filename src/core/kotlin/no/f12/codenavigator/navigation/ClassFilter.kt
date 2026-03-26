package no.f12.codenavigator.navigation

object ClassFilter {
    fun filter(classes: List<ClassInfo>, pattern: String): List<ClassInfo> {
        val regex = Regex(pattern, RegexOption.IGNORE_CASE)
        return classes.filter { classInfo ->
            regex.containsMatchIn(classInfo.className.value) || regex.containsMatchIn(classInfo.reconstructedSourcePath)
        }
    }
}
