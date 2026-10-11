package no.f12.codenavigator

import no.f12.codenavigator.registry.BuildTool
import no.f12.codenavigator.registry.TaskRegistry
import kotlin.test.Test
import kotlin.test.assertTrue

class HelpTextTest {

    @Test
    fun `listing shows every task once with all its non-format options and nothing more`() {
        for (tool in BuildTool.entries) {
            val text = HelpText.generate(tool)
            val lines = text.lines()
            for (task in TaskRegistry.ALL_TASKS) {
                val name = task.taskName(tool)
                val idx = lines.indexOfFirst { it.startsWith("$name ") }
                assertTrue(idx >= 0, "$tool listing is missing $name")
                val optionLine = lines.getOrNull(idx + 1)?.takeIf { it.startsWith("    ") } ?: ""
                for (param in task.params.filter { it.name != "format" && it.name != "llm" && !it.deprecated }) {
                    assertTrue(optionLine.contains(param.render(tool)), "$tool listing for ${task.goal} is missing option ${param.name}")
                }
                assertTrue(!optionLine.contains("(") && !text.contains("Examples:"), "listing should carry options only, no descriptions or examples")
            }
            assertTrue(lines.size < 200, "$tool listing should stay compact, was ${lines.size} lines")
        }
    }

    @Test
    fun `task view gives one task in full and resolves goal, tool name and alias`() {
        for (task in TaskRegistry.ALL_TASKS) {
            val view = HelpText.generate(BuildTool.GRADLE, task = task.goal)
            assertTrue(view.startsWith("## ${task.taskName(BuildTool.GRADLE)}"), "task view for ${task.goal} should start with its heading")
            for (example in task.examples.map { it.render(task.goal, BuildTool.GRADLE) }) assertTrue(view.contains(example))
        }
        assertTrue(HelpText.generate(BuildTool.MAVEN, task = "cnav:find-usages").startsWith("## cnav:find-usages"))
        assertTrue(HelpText.generate(BuildTool.GRADLE, task = "rename-class").startsWith("## cnavMoveClass"))
        assertTrue(HelpText.generate(BuildTool.GRADLE, task = "nope").startsWith("Unknown task"))
    }

    @Test
    fun `help uses the build tool's own syntax only and points at the task view and agent help`() {
        val maven = HelpText.generate(BuildTool.MAVEN)
        val gradle = HelpText.generate(BuildTool.GRADLE)
        assertTrue(!maven.contains("gradlew") && !maven.contains("--format"))
        assertTrue(!Regex("\\bmvn\\b").containsMatchIn(gradle) && !gradle.contains("-Dformat"))
        assertTrue(gradle.contains("cnavHelp --task=<goal>"))
        assertTrue(gradle.contains(TaskRegistry.AGENT_HELP.taskName(BuildTool.GRADLE)))
    }
}
