package no.f12.codenavigator.registry

enum class BuildTool(
    val command: String,
    private val paramPrefix: String,
) {
    GRADLE("./gradlew", "-P"),
    MAVEN("mvn", "-D");

    fun taskName(goal: String): String = when (this) {
        GRADLE -> TaskDef.goalToGradleTaskName(goal)
        MAVEN -> "cnav:$goal"
    }

    fun param(name: String, value: String): String = "$paramPrefix$name=$value"

    fun paramFlag(name: String): String = "$paramPrefix$name"

    fun usage(goal: String, vararg params: String): String =
        (listOf(command, taskName(goal)) + params).joinToString(" ")
}
