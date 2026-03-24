package no.f12.codenavigator

enum class BuildTool(
    val command: String,
    private val paramPrefix: String,
) {
    GRADLE("./gradlew", "-P"),
    MAVEN("mvn", "-D");

    fun taskName(goal: String): String = when (this) {
        GRADLE -> GRADLE_TASK_NAMES[goal]
            ?: throw IllegalArgumentException("Unknown goal: $goal")
        MAVEN -> "cnav:$goal"
    }

    fun param(name: String, value: String): String = "$paramPrefix$name=$value"

    fun paramFlag(name: String): String = "$paramPrefix$name"

    fun usage(goal: String, vararg params: String): String =
        (listOf(command, taskName(goal)) + params).joinToString(" ")

    companion object {
        private val GRADLE_TASK_NAMES = mapOf(
            "list-classes" to "cnavListClasses",
            "find-class" to "cnavFindClass",
            "find-symbol" to "cnavFindSymbol",
            "class-detail" to "cnavClass",
            "find-callers" to "cnavCallers",
            "find-callees" to "cnavCallees",
            "find-interfaces" to "cnavInterfaces",
            "package-deps" to "cnavDeps",
            "dsm" to "cnavDsm",
            "find-usages" to "cnavUsages",
            "hotspots" to "cnavHotspots",
            "churn" to "cnavChurn",
            "code-age" to "cnavAge",
            "authors" to "cnavAuthors",
            "coupling" to "cnavCoupling",
            "help" to "cnavHelp",
            "agent-help" to "cnavAgentHelp",
            "config-help" to "cnavHelpConfig",
        )
    }
}
