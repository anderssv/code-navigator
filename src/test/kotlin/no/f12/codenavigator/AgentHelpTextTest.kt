package no.f12.codenavigator

import no.f12.codenavigator.registry.BuildTool
import no.f12.codenavigator.registry.TaskKind
import no.f12.codenavigator.registry.TaskRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Structural invariants of the agent help. Deliberately not assertions on wording: the prose
 * should be free to change, while coverage, budgets, cross-references and build-tool parity are not.
 */
class AgentHelpTextTest {

    private val tools = BuildTool.entries
    private val allTaskNames = tools.associateWith { tool -> TaskRegistry.ALL_TASKS.map { it.taskName(tool) } }

    private fun everyOutput(tool: BuildTool): Map<String, String> = buildMap {
        put("default", AgentHelpText.generate(tool))
        for (s in AgentHelpText.SECTIONS) put("section=$s", AgentHelpText.generate(tool, section = s))
        for (t in AgentHelpText.TOPICS) put("topic=$t", AgentHelpText.generate(tool, topic = t))
        for (task in TaskRegistry.ALL_TASKS) put("task=${task.goal}", AgentHelpText.generate(tool, task = task.goal))
    }

    private fun words(s: String) = s.split(Regex("\\s+")).count { it.isNotBlank() }

    // ---- layer budgets

    @Test
    fun `install snippet is a short pointer with rules, not a task reference`() {
        for (tool in tools) {
            val text = AgentHelpText.generate(tool, section = "install")
            val lines = text.trimEnd().lines().size
            assertTrue(lines <= 20, "$tool install snippet should fit in 20 lines, was $lines")
            assertTrue(words(text) <= 320, "$tool install snippet should stay under ~400 tokens, was ${words(text)} words")
            assertTrue(text.contains(tool.taskName("agent-help")), "install must point at agent-help")
            assertTrue(text.contains(tool.param("format", "llm")), "install must carry the format rule")
            assertTrue(text.contains("grep"), "install must carry the grep rule")
        }
    }

    @Test
    fun `the guide stays readable in one sitting`() {
        for (tool in tools) {
            val text = AgentHelpText.generate(tool)
            assertTrue(words(text) <= 1900, "$tool guide should stay under ~2500 tokens, was ${words(text)} words")
            assertTrue(text.contains("## Task directory"), "guide must carry the task directory")
            assertTrue(text.contains("## Refactoring protocol"), "guide must carry the refactoring protocol")
            assertTrue(text.contains(tool.param("task", "<goal>")), "guide must point at the per-task view")
        }
    }

    @Test
    fun `default output does not repeat the on-demand sections`() {
        val text = AgentHelpText.generate(BuildTool.GRADLE)
        for (heading in listOf("JSON Schemas", "Reading the results", "Getting Started", "Recommended Workflow", "Setup")) {
            assertTrue(!text.contains("# code-navigator: $heading"), "default output should not inline the '$heading' section")
        }
    }

    // ---- coverage

    @Test
    fun `task directory lists every task once`() {
        for (tool in tools) {
            val text = AgentHelpText.generate(tool)
            for (task in TaskRegistry.ALL_TASKS) {
                val name = task.taskName(tool)
                assertTrue(Regex("^  ${Regex.escape(name)}\\s", RegexOption.MULTILINE).containsMatchIn(text), "$tool directory is missing $name")
            }
        }
    }

    @Test
    fun `every task has a per-task view with all its non-format params and examples`() {
        for (tool in tools) {
            for (task in TaskRegistry.ALL_TASKS) {
                val view = AgentHelpText.generate(tool, task = task.goal)
                assertTrue(view.startsWith("## ${task.taskName(tool)}"), "task view for ${task.goal} should start with its heading, got: ${view.take(60)}")
                for (param in task.params.filter { it.name != "format" && it.name != "llm" && !it.deprecated }) {
                    assertTrue(view.contains(param.render(tool)), "task view for ${task.goal} ($tool) is missing param ${param.name}")
                }
                for (example in task.examples.map { it.render(task.goal, tool) }) {
                    assertTrue(view.contains(example), "task view for ${task.goal} ($tool) is missing example $example")
                }
            }
        }
    }

    @Test
    fun `task view resolves goal, build-tool task name and alias`() {
        assertTrue(AgentHelpText.generate(BuildTool.GRADLE, task = "cnavFindUsages").startsWith("## cnavFindUsages"))
        assertTrue(AgentHelpText.generate(BuildTool.MAVEN, task = "cnav:find-usages").startsWith("## cnav:find-usages"))
        assertTrue(AgentHelpText.generate(BuildTool.GRADLE, task = "rename-class").startsWith("## cnavMoveClass"))
        assertTrue(AgentHelpText.generate(BuildTool.GRADLE, task = "nope").startsWith("Unknown task"))
    }

    @Test
    fun `every mutation task appears in the refactoring lookup and the guide protocol`() {
        for (tool in tools) {
            val refactor = AgentHelpText.generate(tool, section = "refactor")
            val guide = AgentHelpText.generate(tool)
            for (task in TaskRegistry.ALL_TASKS.filter { it.kind == TaskKind.MUTATION }) {
                assertTrue(task.intent != null, "mutation task ${task.goal} needs an intent")
                assertTrue(refactor.contains(task.taskName(tool)), "refactor section ($tool) is missing ${task.taskName(tool)}")
                assertTrue(guide.contains(task.taskName(tool)), "guide ($tool) is missing ${task.taskName(tool)}")
            }
        }
    }

    @Test
    fun `schemas, interpretation and questions sections cover every task that documents them`() {
        for (tool in tools) {
            val schemas = AgentHelpText.generate(tool, section = "schemas")
            val interp = AgentHelpText.generate(tool, section = "interpretation")
            val questions = AgentHelpText.generate(tool, section = "questions")
            for (task in TaskRegistry.ALL_TASKS) {
                if (task.doc.schema.isNotEmpty()) assertTrue(schemas.contains("${task.taskName(tool)}:"), "schemas missing ${task.goal}")
                if (task.doc.interpretation.isNotEmpty()) assertTrue(interp.contains("## ${task.taskName(tool)}"), "interpretation missing ${task.goal}")
                if (task.doc.whenToUse.isNotEmpty()) assertTrue(questions.contains(task.taskName(tool)), "questions missing ${task.goal}")
            }
        }
    }

    // ---- cross references

    @Test
    fun `no output references a task name that does not exist`() {
        for (tool in tools) {
            val pattern = when (tool) {
                BuildTool.GRADLE -> Regex("\\bcnav[A-Z][A-Za-z]+")
                BuildTool.MAVEN -> Regex("\\bcnav:[a-z-]+")
            }
            val known = allTaskNames.getValue(tool).toSet() + TaskRegistry.ALL_TASKS.flatMap { t -> t.aliases.map { tool.taskName(it) } } +
                TaskRegistry.ALL_TASKS.mapNotNull { it.legacyGradleTaskName }
            for ((label, text) in everyOutput(tool)) {
                val dangling = pattern.findAll(text).map { it.value.trimEnd('.', ',', ':', ')') }.filter { it !in known }.toSet()
                assertTrue(dangling.isEmpty(), "$tool $label references unknown task names: $dangling")
            }
        }
    }

    @Test
    fun `unknown section and topic list the valid names`() {
        val s = AgentHelpText.generate(BuildTool.GRADLE, section = "nope")
        val t = AgentHelpText.generate(BuildTool.GRADLE, topic = "nope")
        for (name in AgentHelpText.SECTIONS) assertTrue(s.contains(name), "section error should list $name")
        for (name in AgentHelpText.TOPICS) assertTrue(t.contains(name), "topic error should list $name")
    }

    // ---- build-tool parity

    @Test
    fun `Gradle and Maven outputs differ only in command syntax`() {
        val gradle = everyOutput(BuildTool.GRADLE)
        val maven = everyOutput(BuildTool.MAVEN)
        assertEquals(gradle.keys, maven.keys)
        for (label in gradle.keys) {
            assertEquals(normalise(gradle.getValue(label), BuildTool.GRADLE), normalise(maven.getValue(label), BuildTool.MAVEN), "parity broken in $label")
        }
    }

    @Test
    fun `Maven output never mentions gradlew and Gradle output never mentions mvn`() {
        for ((label, text) in everyOutput(BuildTool.MAVEN)) {
            assertTrue(!text.contains("gradlew") && !text.contains("--format"), "Maven $label leaks Gradle syntax")
        }
        for ((label, text) in everyOutput(BuildTool.GRADLE)) {
            assertTrue(!Regex("\\bmvn\\b").containsMatchIn(text) && !text.contains("-Dformat"), "Gradle $label leaks Maven syntax")
        }
    }

    /** Collapse everything the build tool legitimately changes: task names, param prefixes, command, plugin id, and the whitespace padding around them. */
    private fun normalise(text: String, tool: BuildTool): String {
        var s = text
        for (task in TaskRegistry.ALL_TASKS) {
            s = s.replace(task.taskName(tool), "T:${task.goal}")
            for (alias in task.aliases) s = s.replace(tool.taskName(alias), "T:$alias")
        }
        s = when (tool) {
            BuildTool.GRADLE -> s.replace("./gradlew", "CMD").replace(Regex("(?<![-A-Za-z])--([a-z][a-z-]*)(?![-A-Za-z])"), "P:$1").replace("no.f12.code-navigator", "PLUGIN").replace("Gradle", "TOOL")
            BuildTool.MAVEN -> s.replace("mvn", "CMD").replace(Regex("(?<![-A-Za-z])-D([a-z][a-z-]*)(?![-A-Za-z])"), "P:$1").replace("code-navigator-maven-plugin", "PLUGIN").replace("Maven", "TOOL")
        }
        // setup section: permission examples and troubleshooting steps are intentionally tool-specific
        s = s.replace(Regex("Example: if the prompt shows.*?\\n(If the command has a preamble.*?\\n)?", RegexOption.DOT_MATCHES_ALL), "EXAMPLE\n")
        s = s.replace(Regex("## Troubleshooting.*", RegexOption.DOT_MATCHES_ALL), "TROUBLESHOOTING")
        return s.replace(Regex("[ \\t]+"), " ")
    }
}
