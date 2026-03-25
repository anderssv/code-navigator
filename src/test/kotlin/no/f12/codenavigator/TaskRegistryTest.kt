package no.f12.codenavigator

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNotNull

class ParamDefTest {

    @Test
    fun `stores name, value placeholder, and description`() {
        val param = ParamDef("pattern", "<regex>", "Class/symbol regex", flag = false, defaultValue = null)

        assertEquals("pattern", param.name)
        assertEquals("<regex>", param.valuePlaceholder)
        assertEquals("Class/symbol regex", param.description)
        assertEquals(false, param.flag)
        assertEquals(null, param.defaultValue)
    }

    @Test
    fun `renders as Gradle parameter syntax`() {
        val param = ParamDef("pattern", "<regex>", "Class/symbol regex", flag = false, defaultValue = null)

        assertEquals("-Ppattern=<regex>", param.render(BuildTool.GRADLE))
    }

    @Test
    fun `renders as Maven parameter syntax`() {
        val param = ParamDef("pattern", "<regex>", "Class/symbol regex", flag = false, defaultValue = null)

        assertEquals("-Dpattern=<regex>", param.render(BuildTool.MAVEN))
    }

    @Test
    fun `flag param renders without value`() {
        val param = ParamDef("no-follow", "", "Disable rename tracking", flag = true, defaultValue = null)

        assertEquals("-Pno-follow", param.render(BuildTool.GRADLE))
        assertEquals("-Dno-follow", param.render(BuildTool.MAVEN))
    }
}

class TaskDefTest {

    @Test
    fun `stores goal, description, params, and requiresCompilation`() {
        val pattern = ParamDef("pattern", "<regex>", "Class/symbol regex", flag = false, defaultValue = null)
        val task = TaskDef(
            goal = "find-class",
            description = "Find classes by regex",
            params = listOf(pattern),
            requiresCompilation = true,
        )

        assertEquals("find-class", task.goal)
        assertEquals("Find classes by regex", task.description)
        assertEquals(listOf(pattern), task.params)
        assertEquals(true, task.requiresCompilation)
    }

    @Test
    fun `resolves Gradle task name`() {
        val task = TaskDef(
            goal = "find-class",
            description = "Find classes by regex",
            params = emptyList(),
            requiresCompilation = true,
        )

        assertEquals("cnavFindClass", task.taskName(BuildTool.GRADLE))
    }

    @Test
    fun `resolves Maven goal name`() {
        val task = TaskDef(
            goal = "find-class",
            description = "Find classes by regex",
            params = emptyList(),
            requiresCompilation = true,
        )

        assertEquals("cnav:find-class", task.taskName(BuildTool.MAVEN))
    }
}

class TaskRegistryTest {

    @Test
    fun `contains all 20 goals`() {
        val goals = TaskRegistry.ALL_TASKS.map { it.goal }.toSet()

        assertEquals(20, goals.size)
        assertTrue(goals.contains("find-class"))
        assertTrue(goals.contains("hotspots"))
        assertTrue(goals.contains("help"))
    }

    @Test
    fun `every goal in GRADLE_TASK_NAMES has a matching TaskDef`() {
        val registryGoals = TaskRegistry.ALL_TASKS.map { it.goal }.toSet()

        for (task in TaskRegistry.ALL_TASKS) {
            val gradleName = task.taskName(BuildTool.GRADLE)
            assertNotNull(gradleName, "Goal '${task.goal}' should resolve to a Gradle task name")
        }
        assertEquals(20, registryGoals.size)
    }

    @Test
    fun `navigation tasks require compilation`() {
        val navigationGoals = listOf(
            "list-classes", "find-class", "find-symbol", "class-detail",
            "find-callers", "find-callees", "find-interfaces", "package-deps",
            "dsm", "find-usages", "rank", "dead",
        )

        for (goal in navigationGoals) {
            val task = TaskRegistry.ALL_TASKS.first { it.goal == goal }
            assertTrue(task.requiresCompilation, "Navigation task '$goal' should require compilation")
        }
    }

    @Test
    fun `git analysis tasks do not require compilation`() {
        val gitGoals = listOf("hotspots", "churn", "code-age", "authors", "coupling")

        for (goal in gitGoals) {
            val task = TaskRegistry.ALL_TASKS.first { it.goal == goal }
            assertTrue(!task.requiresCompilation, "Git analysis task '$goal' should not require compilation")
        }
    }

    @Test
    fun `help tasks do not require compilation and have no params`() {
        val helpGoals = listOf("help", "agent-help", "config-help")

        for (goal in helpGoals) {
            val task = TaskRegistry.ALL_TASKS.first { it.goal == goal }
            assertTrue(!task.requiresCompilation, "Help task '$goal' should not require compilation")
            assertTrue(task.params.isEmpty(), "Help task '$goal' should have no params")
        }
    }

    @Test
    fun `find-usages has ownerClass, method, type, and outside-package params`() {
        val paramNames = TaskRegistry.FIND_USAGES.params.map { it.name }.toSet()

        assertTrue(paramNames.contains("ownerClass"))
        assertTrue(paramNames.contains("method"))
        assertTrue(paramNames.contains("type"))
        assertTrue(paramNames.contains("outside-package"))
    }

    @Test
    fun `dsm has root-package, dsm-depth, dsm-html, cycles, and cycle params`() {
        val paramNames = TaskRegistry.DSM.params.map { it.name }.toSet()

        assertTrue(paramNames.contains("root-package"))
        assertTrue(paramNames.contains("dsm-depth"))
        assertTrue(paramNames.contains("dsm-html"))
        assertTrue(paramNames.contains("cycles"))
        assertTrue(paramNames.contains("cycle"))
    }

    @Test
    fun `coupling has after, min-shared-revs, min-coupling, max-changeset-size, and no-follow params`() {
        val paramNames = TaskRegistry.COUPLING.params.map { it.name }.toSet()

        assertTrue(paramNames.contains("after"))
        assertTrue(paramNames.contains("min-shared-revs"))
        assertTrue(paramNames.contains("min-coupling"))
        assertTrue(paramNames.contains("max-changeset-size"))
        assertTrue(paramNames.contains("no-follow"))
    }

    @Test
    fun `format param is on data tasks but not help tasks`() {
        val findClass = TaskRegistry.FIND_CLASS
        assertTrue(findClass.params.any { it.name == "format" }, "find-class should have format param")

        val help = TaskRegistry.HELP
        assertTrue(help.params.none { it.name == "format" }, "help should not have format param")
    }

    @Test
    fun `no-follow param is a flag`() {
        assertTrue(TaskRegistry.NO_FOLLOW.flag, "no-follow should be a flag param")
    }

    @Test
    fun `TOP param has default value of 50`() {
        assertEquals("50", TaskRegistry.TOP.defaultValue)
    }

    @Test
    fun `AFTER param has default value of 1 year ago`() {
        assertEquals("1 year ago", TaskRegistry.AFTER.defaultValue)
    }

    @Test
    fun `PATTERN param has no default value`() {
        assertEquals(null, TaskRegistry.PATTERN.defaultValue)
    }
}
