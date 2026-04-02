package no.f12.codenavigator

import no.f12.codenavigator.registry.BuildTool
import no.f12.codenavigator.registry.TaskRegistry
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertEquals

class HelpTextTest {

    @Test
    fun `Gradle help text lists all available tasks with Gradle names`() {
        val text = HelpText.generate(BuildTool.GRADLE)

        for (task in TaskRegistry.ALL_TASKS) {
            assertTrue(
                text.contains(task.taskName(BuildTool.GRADLE)),
                "Should contain ${task.taskName(BuildTool.GRADLE)} (goal: ${task.goal})",
            )
        }
    }

    @Test
    fun `Maven help text lists all available tasks with Maven names`() {
        val text = HelpText.generate(BuildTool.MAVEN)

        for (task in TaskRegistry.ALL_TASKS) {
            assertTrue(
                text.contains(task.taskName(BuildTool.MAVEN)),
                "Should contain ${task.taskName(BuildTool.MAVEN)} (goal: ${task.goal})",
            )
        }
    }

    @Test
    fun `Gradle help text uses -P parameters and gradlew command`() {
        val text = HelpText.generate(BuildTool.GRADLE)

        assertTrue(text.contains(TaskRegistry.PATTERN.render(BuildTool.GRADLE)))
        assertTrue(text.contains("./gradlew"))
    }

    @Test
    fun `Maven help text uses -D parameters and mvn command`() {
        val text = HelpText.generate(BuildTool.MAVEN)

        assertTrue(text.contains(TaskRegistry.PATTERN.render(BuildTool.MAVEN)))
        assertTrue(text.contains("mvn"))
        assertFalse(text.contains("./gradlew"), "Maven help should not contain ./gradlew")
        assertFalse(
            text.contains(TaskRegistry.PATTERN.render(BuildTool.GRADLE)),
            "Maven help should not contain -P params",
        )
    }

    @Test
    fun `help text includes usage examples`() {
        val gradleText = HelpText.generate(BuildTool.GRADLE)
        val mavenText = HelpText.generate(BuildTool.MAVEN)

        val gradleFindClass = TaskRegistry.FIND_CLASS.taskName(BuildTool.GRADLE)
        val mavenFindClass = TaskRegistry.FIND_CLASS.taskName(BuildTool.MAVEN)

        assertTrue(gradleText.contains("./gradlew $gradleFindClass -Ppattern=Service"))
        assertTrue(mavenText.contains("mvn $mavenFindClass -Dpattern=Service"))
    }

    @Test
    fun `help text documents cycles parameter for DSM`() {
        val gradleText = HelpText.generate(BuildTool.GRADLE)
        val mavenText = HelpText.generate(BuildTool.MAVEN)

        assertTrue(
            gradleText.contains(BuildTool.GRADLE.param("cycles", "true")),
            "Gradle help should document cycles parameter",
        )
        assertTrue(
            mavenText.contains(BuildTool.MAVEN.param("cycles", "true")),
            "Maven help should document cycles parameter",
        )
    }

    @Test
    fun `Gradle help text includes metrics task`() {
        val text = HelpText.generate(BuildTool.GRADLE)

        assertTrue(
            text.contains(TaskRegistry.METRICS.taskName(BuildTool.GRADLE)),
            "Should list metrics task",
        )
        assertTrue(text.contains("project health snapshot"), "Should describe metrics purpose")
    }

    @Test
    fun `Maven help text includes metrics task`() {
        val text = HelpText.generate(BuildTool.MAVEN)

        assertTrue(
            text.contains(TaskRegistry.METRICS.taskName(BuildTool.MAVEN)),
            "Should list metrics task",
        )
    }

    @Test
    fun `default parameter is GRADLE for backward compatibility`() {
        val defaultText = HelpText.generate()
        val gradleText = HelpText.generate(BuildTool.GRADLE)

        assertTrue(defaultText == gradleText, "Default should produce same output as explicit GRADLE")
    }

    @Test
    fun `every non-format param from TaskRegistry appears in help text`() {
        val text = HelpText.generate(BuildTool.GRADLE)

        val globalParamNames = setOf("format", "llm")
        val helpGoals = setOf("help", "agent-help", "config-help")

        val allParams = TaskRegistry.ALL_TASKS
            .filter { it.goal !in helpGoals }
            .flatMap { it.params }
            .filter { it.name !in globalParamNames }
            .filter { !it.deprecated }
            .distinctBy { it.name }

        val missing = allParams.filter { param ->
            !text.contains(param.render(BuildTool.GRADLE))
        }

        assertEquals(
            emptyList(),
            missing.map { "${it.name} (${it.render(BuildTool.GRADLE)})" },
            "All TaskRegistry params should appear in help text",
        )
    }

    @Test
    fun `help text shows default values for params that have them`() {
        val text = HelpText.generate(BuildTool.GRADLE)

        val callersTask = TaskRegistry.FIND_CALLERS.taskName(BuildTool.GRADLE)
        val calleesTask = TaskRegistry.FIND_CALLEES.taskName(BuildTool.GRADLE)
        val callersSection = text.substringAfter(callersTask)
            .substringBefore(calleesTask)
        assertTrue(
            callersSection.contains("default") || callersSection.contains("optional"),
            "maxdepth should be shown as optional or with default in callers section",
        )
    }

    // --- Pattern Matching section ---

    @Test
    fun `help text contains a Pattern Matching section`() {
        val text = HelpText.generate(BuildTool.GRADLE)

        assertTrue(text.contains("Pattern Matching"), "Should have a Pattern Matching section")
    }

    @Test
    fun `Pattern Matching section explains camelCase enhancement`() {
        val text = HelpText.generate(BuildTool.GRADLE)

        assertTrue(text.contains("OwnCont"), "Should show camelCase shorthand example input")
        assertTrue(text.contains("Own.*Cont"), "Should show expanded regex output")
    }

    @Test
    fun `Pattern Matching section mentions case-insensitive matching`() {
        val text = HelpText.generate(BuildTool.GRADLE)
        val patternSection = text.substringAfter("Pattern Matching")
            .substringBefore("Available tasks:")

        assertTrue(
            patternSection.contains("case-insensitive", ignoreCase = true),
            "Pattern Matching section should mention case-insensitive matching",
        )
    }

    @Test
    fun `Pattern Matching section lists params with camelCase enhancement`() {
        val text = HelpText.generate(BuildTool.GRADLE)
        val patternSection = text.substringAfter("Pattern Matching")
            .substringBefore("Available tasks:")

        assertTrue(patternSection.contains("pattern"), "Should list pattern param")
        assertTrue(patternSection.contains("owner-class"), "Should list owner-class param")
        assertTrue(patternSection.contains("type"), "Should list type param")
    }

    @Test
    fun `Pattern Matching section lists params without enhancement`() {
        val text = HelpText.generate(BuildTool.GRADLE)
        val patternSection = text.substringAfter("Pattern Matching")
            .substringBefore("Available tasks:")

        assertTrue(patternSection.contains("method"), "Should list method as plain regex")
        assertTrue(patternSection.contains("field"), "Should list field as plain regex")
        assertTrue(patternSection.contains("filter"), "Should list filter as plain regex")
    }

    @Test
    fun `Pattern Matching section appears before task listings`() {
        val text = HelpText.generate(BuildTool.GRADLE)
        val patternMatchingIndex = text.indexOf("Pattern Matching")
        val availableTasksIndex = text.indexOf("Available tasks:")

        assertTrue(patternMatchingIndex > 0, "Pattern Matching section should exist")
        assertTrue(
            patternMatchingIndex < availableTasksIndex,
            "Pattern Matching section should appear before task listings",
        )
    }

    @Test
    fun `help text includes agent hint pointing to agent-help`() {
        val gradleText = HelpText.generate(BuildTool.GRADLE)
        val mavenText = HelpText.generate(BuildTool.MAVEN)

        assertTrue(gradleText.contains("AI coding agent"), "Should mention AI coding agents")
        assertTrue(gradleText.contains("cnavAgentHelp"), "Should reference cnavAgentHelp task")
        assertTrue(mavenText.contains("cnav:agent-help"), "Maven should reference cnav:agent-help goal")
    }

    @Test
    fun `distance task section shows top default as all instead of 50`() {
        val text = HelpText.generate(BuildTool.GRADLE)
        val distanceTask = TaskRegistry.DISTANCE.taskName(BuildTool.GRADLE)
        val strengthTask = TaskRegistry.STRENGTH.taskName(BuildTool.GRADLE)
        val distanceSection = text.substringAfter(distanceTask)
            .substringBefore(strengthTask)

        assertTrue(
            distanceSection.contains("default: all"),
            "Distance section should show 'default: all' for top param, but was:\n$distanceSection",
        )
        assertFalse(
            distanceSection.contains("default: 50"),
            "Distance section should NOT show 'default: 50' for top param",
        )
    }

    @Test
    fun `strength task section shows top default as all instead of 50`() {
        val text = HelpText.generate(BuildTool.GRADLE)
        val strengthTask = TaskRegistry.STRENGTH.taskName(BuildTool.GRADLE)
        val agentHelpTask = TaskRegistry.AGENT_HELP.taskName(BuildTool.GRADLE)
        val strengthSection = text.substringAfter(strengthTask)
            .substringBefore(agentHelpTask)

        assertTrue(
            strengthSection.contains("default: all"),
            "Strength section should show 'default: all' for top param, but was:\n$strengthSection",
        )
        assertFalse(
            strengthSection.contains("default: 50"),
            "Strength section should NOT show 'default: 50' for top param",
        )
    }

    @Test
    fun `rank task section still shows top default as 50`() {
        val text = HelpText.generate(BuildTool.GRADLE)
        val rankTask = TaskRegistry.RANK.taskName(BuildTool.GRADLE)
        val deadTask = TaskRegistry.DEAD.taskName(BuildTool.GRADLE)
        val rankSection = text.substringAfter(rankTask)
            .substringBefore(deadTask)

        assertTrue(
            rankSection.contains("default: 50"),
            "Rank section should still show 'default: 50' for top param, but was:\n$rankSection",
        )
    }
}
