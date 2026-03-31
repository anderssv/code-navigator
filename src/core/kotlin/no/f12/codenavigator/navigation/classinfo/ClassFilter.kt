package no.f12.codenavigator.navigation.classinfo

object ClassFilter {
    fun filter(classes: List<ClassInfo>, pattern: String): List<ClassInfo> {
        val regex = Regex(pattern, RegexOption.IGNORE_CASE)
        val isQualified = '.' in pattern
        return classes.filter { classInfo ->
            if (isQualified) {
                classInfo.className.matches(regex) || regex.containsMatchIn(classInfo.reconstructedSourcePath)
            } else {
                regex.containsMatchIn(classInfo.className.simpleName()) || regex.containsMatchIn(classInfo.sourceFileName)
            }
        }
    }
}
