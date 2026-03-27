package no.f12.codenavigator

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertEquals
import kotlin.test.assertContains

class AgentHelpTextTest {

    // --- Section parameter and progressive loading ---

    @Test
    fun `AGENT_HELP task has a section parameter`() {
        val sectionParam = TaskRegistry.AGENT_HELP.params.find { it.name == "section" }

        assertTrue(sectionParam != null, "AGENT_HELP should have a section param")
        assertTrue(sectionParam.flag == false, "section should not be a flag")
    }

    @Test
    fun `default output does not contain JSON Schemas section`() {
        val text = AgentHelpText.generate(BuildTool.GRADLE)

        assertFalse(text.contains("--- JSON Schemas ---"), "Default output should not include JSON Schemas")
    }

    @Test
    fun `default output does not contain Recommended Workflow section`() {
        val text = AgentHelpText.generate(BuildTool.GRADLE)

        assertFalse(text.contains("--- Recommended Workflow ---"), "Default output should not include Recommended Workflow")
    }

    @Test
    fun `default output does not contain Result Interpretation section`() {
        val text = AgentHelpText.generate(BuildTool.GRADLE)

        assertFalse(text.contains("--- Result Interpretation ---"), "Default output should not include Result Interpretation")
    }

    @Test
    fun `default output contains Common Questions section`() {
        val text = AgentHelpText.generate(BuildTool.GRADLE)

        assertContains(text, "Common Questions")
    }

    @Test
    fun `default output contains Task Reference section`() {
        val text = AgentHelpText.generate(BuildTool.GRADLE)

        assertContains(text, "--- Task Reference ---")
    }

    @Test
    fun `default output contains section directory listing available sections`() {
        val text = AgentHelpText.generate(BuildTool.GRADLE)

        assertContains(text, "section=workflow")
        assertContains(text, "section=interpretation")
        assertContains(text, "section=schemas")
        assertContains(text, "section=extraction")
    }

    @Test
    fun `install section contains instruction to run agentHelp`() {
        val text = AgentHelpText.generate(BuildTool.GRADLE, section = "install")

        assertContains(text, "cnavAgentHelp")
    }

    @Test
    fun `install section contains all non-help task names`() {
        val text = AgentHelpText.generate(BuildTool.GRADLE, section = "install")

        val helpGoals = setOf("help", "agent-help", "config-help")
        for (task in TaskRegistry.ALL_TASKS.filter { it.goal !in helpGoals }) {
            assertTrue(
                text.contains(task.taskName(BuildTool.GRADLE)),
                "install should mention ${task.taskName(BuildTool.GRADLE)}",
            )
        }
    }

    @Test
    fun `Maven install section uses Maven task names`() {
        val text = AgentHelpText.generate(BuildTool.MAVEN, section = "install")

        assertContains(text, "cnav:agent-help")
        assertFalse(text.contains("cnavAgentHelp"), "Maven install should not contain Gradle task names")
    }

    @Test
    fun `workflow section contains ORIENT FIND INSPECT TRACE steps`() {
        val text = AgentHelpText.generate(BuildTool.GRADLE, section = "workflow")

        assertContains(text, "ORIENT")
        assertContains(text, "FIND")
        assertContains(text, "INSPECT")
        assertContains(text, "TRACE")
        assertContains(text, "MAP")
        assertContains(text, "ANALYZE")
    }

    @Test
    fun `interpretation section contains fan-in fan-out heuristics`() {
        val text = AgentHelpText.generate(BuildTool.GRADLE, section = "interpretation")

        assertContains(text, "Fan-in")
        assertContains(text, "Fan-out")
        assertContains(text, "Dead code")
    }

    @Test
    fun `schemas section contains JSON schema examples`() {
        val text = AgentHelpText.generate(BuildTool.GRADLE, section = "schemas")

        assertContains(text, "className")
        assertContains(text, "sourceFile")
        assertContains(text, "\"children\"")
    }

    @Test
    fun `extraction section contains jq examples and CNAV markers`() {
        val text = AgentHelpText.generate(BuildTool.GRADLE, section = "extraction")

        assertContains(text, "CNAV_BEGIN")
        assertContains(text, "jq")
    }

    @Test
    fun `invalid section returns error listing valid sections`() {
        val text = AgentHelpText.generate(BuildTool.GRADLE, section = "bogus")

        assertContains(text, "Unknown section")
        assertContains(text, "install")
        assertContains(text, "workflow")
        assertContains(text, "schemas")
    }

    @Test
    fun `default output does not contain workflow or interpretation sections`() {
        val defaultText = AgentHelpText.generate(BuildTool.GRADLE)
        val workflowText = AgentHelpText.generate(BuildTool.GRADLE, section = "workflow")
        val schemasText = AgentHelpText.generate(BuildTool.GRADLE, section = "schemas")

        assertTrue(
            defaultText.length < defaultText.length + workflowText.length + schemasText.length,
            "Default should be smaller than combined sections",
        )
    }

    @Test
    fun `Gradle agent help text contains all task names with Gradle names`() {
        val text = AgentHelpText.generate(BuildTool.GRADLE)

        val helpGoals = setOf("help", "agent-help", "config-help")
        for (task in TaskRegistry.ALL_TASKS.filter { it.goal !in helpGoals }) {
            assertTrue(
                text.contains(task.taskName(BuildTool.GRADLE)),
                "Should contain ${task.taskName(BuildTool.GRADLE)} (goal: ${task.goal})",
            )
        }
    }

    @Test
    fun `Maven agent help text contains all task names with Maven names`() {
        val text = AgentHelpText.generate(BuildTool.MAVEN)

        val helpGoals = setOf("help", "agent-help", "config-help")
        for (task in TaskRegistry.ALL_TASKS.filter { it.goal !in helpGoals }) {
            assertTrue(
                text.contains(task.taskName(BuildTool.MAVEN)),
                "Should contain ${task.taskName(BuildTool.MAVEN)} (goal: ${task.goal})",
            )
        }
    }

    @Test
    fun `Gradle agent help text uses -P parameters and gradlew command`() {
        val text = AgentHelpText.generate(BuildTool.GRADLE)

        assertTrue(text.contains(TaskRegistry.LLM.render(BuildTool.GRADLE)))
        assertTrue(text.contains(TaskRegistry.FORMAT.render(BuildTool.GRADLE)))
        assertTrue(text.contains("./gradlew"))
    }

    @Test
    fun `Maven agent help text uses -D parameters and mvn command`() {
        val text = AgentHelpText.generate(BuildTool.MAVEN)

        assertTrue(text.contains(TaskRegistry.LLM.render(BuildTool.MAVEN)))
        assertTrue(text.contains(TaskRegistry.FORMAT.render(BuildTool.MAVEN)))
        assertTrue(text.contains("mvn"))
        assertFalse(text.contains("./gradlew"), "Maven agent help should not contain ./gradlew")
        assertFalse(
            text.contains(TaskRegistry.LLM.render(BuildTool.GRADLE)),
            "Maven agent help should not contain -P params",
        )
    }

    @Test
    fun `default output includes performance tips`() {
        val text = AgentHelpText.generate(BuildTool.GRADLE)

        assertTrue(text.contains("Tips for Optimal Results"))
        assertTrue(text.contains("cached"))
    }

    @Test
    fun `Gradle agent help text includes all parameter renders`() {
        val text = AgentHelpText.generate(BuildTool.GRADLE)

        val paramsToCheck = listOf(
            TaskRegistry.PATTERN,
            TaskRegistry.METHOD,
            TaskRegistry.MAXDEPTH,
            TaskRegistry.PROJECTONLY,
            TaskRegistry.TOP,
            TaskRegistry.AFTER,
            TaskRegistry.MIN_REVS,
            TaskRegistry.NO_FOLLOW,
        )
        for (param in paramsToCheck) {
            assertTrue(
                text.contains(param.render(BuildTool.GRADLE)),
                "Should contain ${param.render(BuildTool.GRADLE)} (param: ${param.name})",
            )
        }
    }

    @Test
    fun `Maven agent help text includes all parameter renders`() {
        val text = AgentHelpText.generate(BuildTool.MAVEN)

        val paramsToCheck = listOf(
            TaskRegistry.PATTERN,
            TaskRegistry.METHOD,
            TaskRegistry.MAXDEPTH,
            TaskRegistry.PROJECTONLY,
            TaskRegistry.TOP,
            TaskRegistry.AFTER,
            TaskRegistry.MIN_REVS,
            TaskRegistry.NO_FOLLOW,
        )
        for (param in paramsToCheck) {
            assertTrue(
                text.contains(param.render(BuildTool.MAVEN)),
                "Should contain ${param.render(BuildTool.MAVEN)} (param: ${param.name})",
            )
        }
    }

    @Test
    fun `schemas section documents JSON schemas for each task`() {
        val text = AgentHelpText.generate(BuildTool.GRADLE, section = "schemas")

        assertTrue(text.contains("className"), "Should document className field")
        assertTrue(text.contains("sourceFile"), "Should document sourceFile field")
        assertTrue(text.contains("sourcePath"), "Should document sourcePath field")
        assertTrue(text.contains("\"method\""), "Should document method field in call trees")
        assertTrue(text.contains("\"children\""), "Should document children field in call trees")
        assertTrue(text.contains("\"interface\""), "Should document interface field")
        assertTrue(text.contains("\"implementors\""), "Should document implementors field")
        assertTrue(text.contains("\"dependencies\""), "Should document dependencies field")
        assertTrue(text.contains("\"dependents\""), "Should document dependents field")
    }

    @Test
    fun `extraction section includes jq examples`() {
        val text = AgentHelpText.generate(BuildTool.GRADLE, section = "extraction")

        assertTrue(text.contains("jq"), "Should mention jq")
        assertTrue(text.contains("| jq"), "Should show pipe to jq")
    }

    @Test
    fun `Gradle extraction section uses gradlew command`() {
        val text = AgentHelpText.generate(BuildTool.GRADLE, section = "extraction")

        val listClassesTask = TaskRegistry.LIST_CLASSES.taskName(BuildTool.GRADLE)
        assertTrue(text.contains("./gradlew $listClassesTask"))
    }

    @Test
    fun `Maven extraction section uses mvn command`() {
        val text = AgentHelpText.generate(BuildTool.MAVEN, section = "extraction")

        val listClassesTask = TaskRegistry.LIST_CLASSES.taskName(BuildTool.MAVEN)
        assertTrue(text.contains("mvn $listClassesTask"))
    }

    @Test
    fun `schemas section documents JSON schema for find-usages`() {
        val text = AgentHelpText.generate(BuildTool.GRADLE, section = "schemas")

        assertTrue(text.contains("\"targetOwner\""), "Should document targetOwner field")
        assertTrue(text.contains("\"targetMethod\""), "Should document targetMethod field")
    }

    @Test
    fun `workflow section mentions migration workflows`() {
        val text = AgentHelpText.generate(BuildTool.GRADLE, section = "workflow")

        assertTrue(text.contains("MIGRATE"), "Should mention migration use case")
    }

    @Test
    fun `default parameter is GRADLE for backward compatibility`() {
        val defaultText = AgentHelpText.generate()
        val gradleText = AgentHelpText.generate(BuildTool.GRADLE)

        assertTrue(defaultText == gradleText, "Default should produce same output as explicit GRADLE")
    }

    @Test
    fun `default output emphasizes one-shot accuracy over iterative grep`() {
        val text = AgentHelpText.generate(BuildTool.GRADLE)

        assertTrue(text.contains("single"), "Should mention getting results in a single call")
        assertTrue(text.contains("tool call"), "Should frame benefit in terms of tool call round-trips")
        assertTrue(text.contains("correct on the first"), "Should emphasize correctness on first response")
        assertTrue(text.contains("No iterative"), "Should explicitly state no iterative searching needed")
    }

    @Test
    fun `agent help text includes when-to-use guidance`() {
        val text = AgentHelpText.generate(BuildTool.GRADLE)

        assertTrue(text.contains("Use code-navigator when"), "Should have when-to-use-cnav section")
        assertTrue(text.contains("Use grep"), "Should have when-to-use-grep section")
    }

    @Test
    fun `Maven extraction section uses mvn and not gradlew`() {
        val text = AgentHelpText.generate(BuildTool.MAVEN, section = "extraction")

        val listClassesTask = TaskRegistry.LIST_CLASSES.taskName(BuildTool.MAVEN)
        assertTrue(text.contains("mvn $listClassesTask"), "Maven extraction example should use mvn command")
        assertFalse(
            text.contains("./gradlew ${TaskRegistry.LIST_CLASSES.taskName(BuildTool.GRADLE)}"),
            "Maven should not contain Gradle extraction examples",
        )
    }

    @Test
    fun `agent help text includes metrics in task reference`() {
        val text = AgentHelpText.generate(BuildTool.GRADLE)

        assertTrue(
            text.contains(TaskRegistry.METRICS.taskName(BuildTool.GRADLE)),
            "Task reference should list metrics task",
        )
        assertTrue(text.contains("health snapshot"), "Should describe metrics purpose")
    }

    @Test
    fun `schemas section documents JSON schema for metrics`() {
        val text = AgentHelpText.generate(BuildTool.GRADLE, section = "schemas")

        assertTrue(text.contains("\"totalClasses\""), "Should document totalClasses field")
        assertTrue(text.contains("\"packageCount\""), "Should document packageCount field")
        assertTrue(text.contains("\"averageFanIn\""), "Should document averageFanIn field")
        assertTrue(text.contains("\"cycleCount\""), "Should document cycleCount field")
    }

    @Test
    fun `task reference lists every non-help task from TaskRegistry`() {
        val text = AgentHelpText.generate(BuildTool.GRADLE)
        val taskReferenceSection = text.substringAfter("--- Task Reference ---")
            .substringBefore("--- Global Parameters ---")

        val helpGoals = setOf("help", "agent-help", "config-help")
        val expectedTasks = TaskRegistry.ALL_TASKS.filter { it.goal !in helpGoals }

        for (task in expectedTasks) {
            val taskName = task.taskName(BuildTool.GRADLE)
            assertTrue(
                taskReferenceSection.contains(taskName),
                "Task Reference should list $taskName (goal: ${task.goal})",
            )
        }
    }

    @Test
    fun `task reference shows all non-format params for each task`() {
        val text = AgentHelpText.generate(BuildTool.GRADLE)
        val taskReferenceSection = text.substringAfter("--- Task Reference ---")
            .substringBefore("--- Global Parameters ---")

        val globalParamNames = setOf("format", "llm")
        val helpGoals = setOf("help", "agent-help", "config-help")

        for (task in TaskRegistry.ALL_TASKS.filter { it.goal !in helpGoals }) {
            val taskName = task.taskName(BuildTool.GRADLE)
            val nonGlobalParams = task.params.filter { it.name !in globalParamNames }

            for (param in nonGlobalParams) {
                val rendered = param.render(BuildTool.GRADLE)
                assertTrue(
                    taskReferenceSection.contains(rendered),
                    "Task Reference should contain $rendered for $taskName (param: ${param.name})",
                )
            }
        }
    }

    @Test
    fun `every non-format param from TaskRegistry appears somewhere in agent help output`() {
        val text = AgentHelpText.generate(BuildTool.GRADLE)

        val globalParamNames = setOf("format", "llm")

        val allParams = TaskRegistry.ALL_TASKS
            .flatMap { it.params }
            .filter { it.name !in globalParamNames }
            .distinctBy { it.name }

        val missing = allParams.filter { param ->
            !text.contains(param.render(BuildTool.GRADLE))
        }

        assertEquals(
            emptyList(),
            missing.map { "${it.name} (${it.render(BuildTool.GRADLE)})" },
            "All TaskRegistry params should appear in agent help output",
        )
    }

    @Test
    fun `global parameters section includes params shared across tasks`() {
        val text = AgentHelpText.generate(BuildTool.GRADLE)
        val globalSection = text.substringAfter("--- Global Parameters ---")
            .substringBefore("--- Tips")

        val sharedParams = listOf(
            TaskRegistry.MAXDEPTH,
            TaskRegistry.PROJECTONLY,
            TaskRegistry.AFTER,
            TaskRegistry.TOP,
        )
        for (param in sharedParams) {
            assertTrue(
                globalSection.contains(param.render(BuildTool.GRADLE)),
                "Global section should list ${param.name}",
            )
        }
    }

    @Test
    fun `global parameters section shows default values from TaskRegistry`() {
        val text = AgentHelpText.generate(BuildTool.GRADLE)
        val globalSection = text.substringAfter("--- Global Parameters ---")
            .substringBefore("--- Tips")

        val paramsWithDefaults = listOf(TaskRegistry.MAXDEPTH, TaskRegistry.TOP)
        for (param in paramsWithDefaults) {
            assertTrue(
                globalSection.contains("default: ${param.defaultValue}"),
                "Global section should show default for ${param.name}: ${param.defaultValue}",
            )
        }
    }

    @Test
    fun `Maven task reference uses Maven task names and -D params`() {
        val text = AgentHelpText.generate(BuildTool.MAVEN)
        val taskReferenceSection = text.substringAfter("--- Task Reference ---")
            .substringBefore("--- Global Parameters ---")

        val callersTask = TaskRegistry.FIND_CALLERS.taskName(BuildTool.MAVEN)
        val methodParam = TaskRegistry.METHOD.render(BuildTool.MAVEN)
        val gradleMethodParam = TaskRegistry.METHOD.render(BuildTool.GRADLE)

        assertTrue(taskReferenceSection.contains(callersTask), "Should use Maven task names")
        assertTrue(taskReferenceSection.contains(methodParam), "Should use -D params")
        assertFalse(taskReferenceSection.contains(gradleMethodParam), "Should not use -P params")
    }


    @Test
    fun `common questions maps type usage question to find-usages with type param`() {
        val text = AgentHelpText.generate(BuildTool.GRADLE)
        val commonQuestionsSection = text.substringAfter("Common Questions")
            .substringBefore("--- Task Reference ---")

        val findUsagesTask = TaskRegistry.FIND_USAGES.taskName(BuildTool.GRADLE)
        val typeParam = TaskRegistry.FIND_USAGES.paramByName("type").render(BuildTool.GRADLE)

        assertTrue(
            commonQuestionsSection.contains(findUsagesTask),
            "Common Questions should mention $findUsagesTask for type usage",
        )
        assertTrue(
            commonQuestionsSection.contains(typeParam) || commonQuestionsSection.contains("-Ptype"),
            "Common Questions should mention -Ptype param for type usage",
        )
    }

    @Test
    fun `common questions maps caller question to find-callers with method param`() {
        val text = AgentHelpText.generate(BuildTool.GRADLE)
        val commonQuestionsSection = text.substringAfter("Common Questions")
            .substringBefore("--- Task Reference ---")

        val findCallersTask = TaskRegistry.FIND_CALLERS.taskName(BuildTool.GRADLE)
        val methodParam = TaskRegistry.METHOD.render(BuildTool.GRADLE)

        assertTrue(
            commonQuestionsSection.contains(findCallersTask),
            "Common Questions should mention $findCallersTask",
        )
        assertTrue(
            commonQuestionsSection.contains(methodParam) || commonQuestionsSection.contains("-Pmethod"),
            "Common Questions should mention -Pmethod param",
        )
    }

    @Test
    fun `common questions maps class inspection question to class-detail`() {
        val text = AgentHelpText.generate(BuildTool.GRADLE)
        val commonQuestionsSection = text.substringAfter("Common Questions")
            .substringBefore("--- Task Reference ---")

        val classDetailTask = TaskRegistry.CLASS_DETAIL.taskName(BuildTool.GRADLE)

        assertTrue(
            commonQuestionsSection.contains(classDetailTask),
            "Common Questions should mention $classDetailTask",
        )
    }

    @Test
    fun `Maven common questions uses Maven task names and params`() {
        val text = AgentHelpText.generate(BuildTool.MAVEN)
        val commonQuestionsSection = text.substringAfter("Common Questions")
            .substringBefore("--- Task Reference ---")

        val findUsagesTask = TaskRegistry.FIND_USAGES.taskName(BuildTool.MAVEN)

        assertTrue(
            commonQuestionsSection.contains(findUsagesTask),
            "Maven Common Questions should use Maven task name $findUsagesTask",
        )
    }

    @Test
    fun `interpretation section contains Result Interpretation heading`() {
        val text = AgentHelpText.generate(BuildTool.GRADLE, section = "interpretation")

        assertTrue(text.contains("Result Interpretation"), "Should have a Result Interpretation section")
    }
}
