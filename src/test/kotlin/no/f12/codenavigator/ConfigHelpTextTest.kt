package no.f12.codenavigator

import no.f12.codenavigator.registry.BuildTool
import no.f12.codenavigator.registry.TaskCategory
import no.f12.codenavigator.registry.TaskRegistry
import kotlin.test.Test
import kotlin.test.assertTrue

class ConfigHelpTextTest {

    @Test
    fun `lists all global parameters for Gradle`() {
        val text = ConfigHelpText.generate(BuildTool.GRADLE)

        assertTrue(text.contains(TaskRegistry.FORMAT.render(BuildTool.GRADLE)))
        assertTrue(text.contains(TaskRegistry.LLM.render(BuildTool.GRADLE)))
    }

    @Test
    fun `lists all global parameters for Maven`() {
        val text = ConfigHelpText.generate(BuildTool.MAVEN)

        assertTrue(text.contains(TaskRegistry.FORMAT.render(BuildTool.MAVEN)))
        assertTrue(text.contains(TaskRegistry.LLM.render(BuildTool.MAVEN)))
    }

    @Test
    fun `Gradle uses -P prefix and gradlew command`() {
        val text = ConfigHelpText.generate(BuildTool.GRADLE)

        assertTrue(text.contains(TaskRegistry.PATTERN.render(BuildTool.GRADLE)))
        assertTrue(text.contains("./gradlew"))
        assertTrue(text.contains("Gradle project properties (-P flags)"))
    }

    @Test
    fun `Maven uses -D prefix and mvn command`() {
        val text = ConfigHelpText.generate(BuildTool.MAVEN)

        assertTrue(text.contains(TaskRegistry.PATTERN.render(BuildTool.MAVEN)))
        assertTrue(text.contains("mvn"))
        assertTrue(text.contains("Maven system properties (-D flags)"))
    }

    @Test
    fun `Gradle lists navigation task parameters`() {
        val text = ConfigHelpText.generate(BuildTool.GRADLE)

        val navParams = listOf(
            TaskRegistry.PATTERN,
            TaskRegistry.METHOD,
            TaskRegistry.MAXDEPTH,
            TaskRegistry.PROJECTONLY,
        )
        for (param in navParams) {
            assertTrue(
                text.contains(param.render(BuildTool.GRADLE)),
                "Should contain ${param.name}",
            )
        }
    }

    @Test
    fun `Maven lists navigation task parameters`() {
        val text = ConfigHelpText.generate(BuildTool.MAVEN)

        val navParams = listOf(
            TaskRegistry.PATTERN,
            TaskRegistry.METHOD,
            TaskRegistry.MAXDEPTH,
            TaskRegistry.PROJECTONLY,
        )
        for (param in navParams) {
            assertTrue(
                text.contains(param.render(BuildTool.MAVEN)),
                "Should contain ${param.name}",
            )
        }
    }

    @Test
    fun `lists DSM parameters`() {
        val text = ConfigHelpText.generate(BuildTool.GRADLE)

        assertTrue(text.contains("root-package="))
        assertTrue(text.contains("dsm-depth="))
        assertTrue(text.contains("dsm-html="))
        assertTrue(text.contains(BuildTool.GRADLE.param("cycles", "true")))
    }

    @Test
    fun `lists git analysis parameters`() {
        val text = ConfigHelpText.generate(BuildTool.GRADLE)

        val gitParams = listOf(
            TaskRegistry.AFTER,
            TaskRegistry.TOP,
            TaskRegistry.MIN_REVS,
            TaskRegistry.NO_FOLLOW,
        )
        for (param in gitParams) {
            assertTrue(
                text.contains(param.render(BuildTool.GRADLE)),
                "Should contain ${param.name}",
            )
        }
    }

    @Test
    fun `Gradle indicates which tasks use each parameter`() {
        val text = ConfigHelpText.generate(BuildTool.GRADLE)

        val expectedTasks = listOf(
            TaskRegistry.FIND_CLASS,
            TaskRegistry.FIND_CALLERS,
            TaskRegistry.DSM,
            TaskRegistry.HOTSPOTS,
        )
        for (task in expectedTasks) {
            assertTrue(
                text.contains(task.taskName(BuildTool.GRADLE)),
                "Should mention ${task.taskName(BuildTool.GRADLE)}",
            )
        }
    }

    @Test
    fun `Maven indicates which tasks use each parameter with Maven names`() {
        val text = ConfigHelpText.generate(BuildTool.MAVEN)

        val expectedTasks = listOf(
            TaskRegistry.FIND_CLASS,
            TaskRegistry.FIND_CALLERS,
            TaskRegistry.DSM,
            TaskRegistry.HOTSPOTS,
        )
        for (task in expectedTasks) {
            assertTrue(
                text.contains(task.taskName(BuildTool.MAVEN)),
                "Should mention ${task.taskName(BuildTool.MAVEN)}",
            )
        }
    }

    @Test
    fun `includes default values where applicable`() {
        val text = ConfigHelpText.generate(BuildTool.GRADLE)

        assertTrue(text.contains("default:"), "Should show default values")
    }

    @Test
    fun `lists find-usages parameters for Gradle`() {
        val text = ConfigHelpText.generate(BuildTool.GRADLE)

        assertTrue(text.contains("-Powner-class="), "Should list owner-class parameter")
        assertTrue(text.contains("-Ptype="), "Should list type parameter")
        assertTrue(
            text.contains(TaskRegistry.FIND_USAGES.taskName(BuildTool.GRADLE)),
            "owner/type should reference usages task",
        )
    }

    @Test
    fun `lists find-usages parameters for Maven`() {
        val text = ConfigHelpText.generate(BuildTool.MAVEN)

        assertTrue(text.contains("-Downer-class="), "Should list owner-class parameter for Maven")
        assertTrue(text.contains("-Dtype="), "Should list type parameter for Maven")
        assertTrue(
            text.contains(TaskRegistry.FIND_USAGES.taskName(BuildTool.MAVEN)),
            "owner/type should reference find-usages goal",
        )
    }

    @Test
    fun `method parameter mentions find-usages task`() {
        val text = ConfigHelpText.generate(BuildTool.GRADLE)

        val methodLine = text.lines().first { it.contains(TaskRegistry.METHOD.render(BuildTool.GRADLE)) }
        assertTrue(
            methodLine.contains(TaskRegistry.FIND_USAGES.taskName(BuildTool.GRADLE)),
            "method parameter should mention usages task",
        )
    }

    @Test
    fun `default parameter is GRADLE for backward compatibility`() {
        val defaultText = ConfigHelpText.generate()
        val gradleText = ConfigHelpText.generate(BuildTool.GRADLE)

        assertTrue(defaultText == gradleText, "Default should produce same output as explicit GRADLE")
    }

    @Test
    fun `lists composite analysis section with balance parameters`() {
        val text = ConfigHelpText.generate(BuildTool.GRADLE)

        assertTrue(text.contains("Composite Analysis"), "Should have Composite Analysis section")
        assertTrue(
            text.contains(TaskRegistry.BALANCE.taskName(BuildTool.GRADLE)),
            "Should mention cnavBalance task",
        )
    }
}
