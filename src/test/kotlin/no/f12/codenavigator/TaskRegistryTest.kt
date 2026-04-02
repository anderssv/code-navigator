package no.f12.codenavigator

import no.f12.codenavigator.registry.BuildTool
import no.f12.codenavigator.registry.ParamDef
import no.f12.codenavigator.registry.ParamType
import no.f12.codenavigator.registry.TaskCategory
import no.f12.codenavigator.registry.TaskDef
import no.f12.codenavigator.registry.TaskRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNotNull
import kotlin.test.assertFailsWith

class ParamDefTest {

    @Test
    fun `stores name, value placeholder, and description`() {
        val param = ParamDef("pattern", "<regex>", "Class/symbol regex", flag = false, defaultValue = null, enhancePattern = false, type = ParamType.STRING)

        assertEquals("pattern", param.name)
        assertEquals("<regex>", param.valuePlaceholder)
        assertEquals("Class/symbol regex", param.description)
        assertEquals(false, param.flag)
        assertEquals(null, param.defaultValue)
        assertEquals(false, param.enhancePattern)
    }

    @Test
    fun `renders as Gradle parameter syntax`() {
        val param = ParamDef("pattern", "<regex>", "Class/symbol regex", flag = false, defaultValue = null, enhancePattern = false, type = ParamType.STRING)

        assertEquals("-Ppattern=<regex>", param.render(BuildTool.GRADLE))
    }

    @Test
    fun `renders as Maven parameter syntax`() {
        val param = ParamDef("pattern", "<regex>", "Class/symbol regex", flag = false, defaultValue = null, enhancePattern = false, type = ParamType.STRING)

        assertEquals("-Dpattern=<regex>", param.render(BuildTool.MAVEN))
    }

    @Test
    fun `flag param renders without value`() {
        val param = ParamDef("no-follow", "", "Disable rename tracking", flag = true, defaultValue = null, enhancePattern = false, type = ParamType.STRING)

        assertEquals("-Pno-follow", param.render(BuildTool.GRADLE))
        assertEquals("-Dno-follow", param.render(BuildTool.MAVEN))
    }

    @Test
    fun `parse returns value for STRING`() {
        val param = ParamDef("pattern", "<regex>", "Pattern", flag = false, defaultValue = null, enhancePattern = false, type = ParamType.STRING)

        assertEquals("MyService", param.parse("MyService"))
    }

    @Test
    fun `parse returns null for STRING when value is null`() {
        val param = ParamDef("pattern", "<regex>", "Pattern", flag = false, defaultValue = null, enhancePattern = false, type = ParamType.STRING)

        assertEquals(null, param.parse(null))
    }

    @Test
    fun `parse returns default from ParamDef when value is null for BOOLEAN`() {
        val param = ParamDef("project-only", "true", "Hide stdlib", flag = false, defaultValue = "true", enhancePattern = false, type = ParamType.BOOLEAN)

        assertEquals(true, param.parse(null))
    }

    @Test
    fun `parse returns true when value is true for BOOLEAN`() {
        val param = ParamDef("project-only", "true", "Hide stdlib", flag = false, defaultValue = "false", enhancePattern = false, type = ParamType.BOOLEAN)

        assertEquals(true, param.parse("true"))
    }

    @Test
    fun `parse returns false when value is false for BOOLEAN`() {
        val param = ParamDef("project-only", "true", "Hide stdlib", flag = false, defaultValue = "true", enhancePattern = false, type = ParamType.BOOLEAN)

        assertEquals(false, param.parse("false"))
    }

    @Test
    fun `parse returns false when no defaultValue and value is null for BOOLEAN`() {
        val param = ParamDef("detail", "true", "Show details", flag = false, defaultValue = null, enhancePattern = false, type = ParamType.BOOLEAN)

        assertEquals(false, param.parse(null))
    }

    @Test
    fun `parse returns default from ParamDef when value is null for INT`() {
        val param = ParamDef("top", "<N>", "Max results", flag = false, defaultValue = "50", enhancePattern = false, type = ParamType.INT)

        assertEquals(50, param.parse(null))
    }

    @Test
    fun `parse returns parsed int when value is valid for INT`() {
        val param = ParamDef("top", "<N>", "Max results", flag = false, defaultValue = "50", enhancePattern = false, type = ParamType.INT)

        assertEquals(10, param.parse("10"))
    }

    @Test
    fun `parse returns default when value is non-numeric for INT`() {
        val param = ParamDef("top", "<N>", "Max results", flag = false, defaultValue = "50", enhancePattern = false, type = ParamType.INT)

        assertEquals(50, param.parse("abc"))
    }

    @Test
    fun `parseFrom returns true for FLAG when key is present`() {
        val param = ParamDef("no-follow", "", "Disable rename tracking", flag = true, defaultValue = null, enhancePattern = false, type = ParamType.FLAG)

        assertEquals(true, param.parseFrom(mapOf("no-follow" to null)))
    }

    @Test
    fun `parseFrom returns false for FLAG when key is absent`() {
        val param = ParamDef("no-follow", "", "Disable rename tracking", flag = true, defaultValue = null, enhancePattern = false, type = ParamType.FLAG)

        assertEquals(false, param.parseFrom(emptyMap()))
    }

    @Test
    fun `parse splits comma-separated values for LIST_STRING`() {
        val param = ParamDef("exclude-annotated", "<ann>", "Annotations", flag = false, defaultValue = null, enhancePattern = false, type = ParamType.LIST_STRING)

        val result = param.parse("RestController, Scheduled , Component")

        assertEquals(listOf("RestController", "Scheduled", "Component"), result)
    }

    @Test
    fun `parse returns empty list for null value for LIST_STRING`() {
        val param = ParamDef("exclude-annotated", "<ann>", "Annotations", flag = false, defaultValue = null, enhancePattern = false, type = ParamType.LIST_STRING)

        val result = param.parse(null)

        assertEquals(emptyList<String>(), result)
    }

    @Test
    fun `parse filters out blank entries for LIST_STRING`() {
        val param = ParamDef("exclude-annotated", "<ann>", "Annotations", flag = false, defaultValue = null, enhancePattern = false, type = ParamType.LIST_STRING)

        val result = param.parse("RestController,,, Scheduled")

        assertEquals(listOf("RestController", "Scheduled"), result)
    }

    @Test
    fun `parseFormat returns TEXT when no format or llm specified`() {
        val result = ParamDef.parseFormat(emptyMap())

        assertEquals(no.f12.codenavigator.config.OutputFormat.TEXT, result)
    }

    @Test
    fun `parseFormat returns JSON when format is json`() {
        val result = ParamDef.parseFormat(mapOf("format" to "json"))

        assertEquals(no.f12.codenavigator.config.OutputFormat.JSON, result)
    }

    @Test
    fun `parseFormat returns LLM when llm is true`() {
        val result = ParamDef.parseFormat(mapOf("llm" to "true"))

        assertEquals(no.f12.codenavigator.config.OutputFormat.LLM, result)
    }

    @Test
    fun `parseFormat returns LLM when format is llm`() {
        val result = ParamDef.parseFormat(mapOf("format" to "llm"))

        assertEquals(no.f12.codenavigator.config.OutputFormat.LLM, result)
    }

    @Test
    fun `parse returns parsed date when value is valid for DATE`() {
        val param = ParamDef("after", "YYYY-MM-DD", "After date", flag = false, defaultValue = "1 year ago", enhancePattern = false, type = ParamType.DATE)

        assertEquals(java.time.LocalDate.of(2025, 6, 15), param.parse("2025-06-15"))
    }

    @Test
    fun `parse returns one year ago when value is null for DATE`() {
        val param = ParamDef("after", "YYYY-MM-DD", "After date", flag = false, defaultValue = "1 year ago", enhancePattern = false, type = ParamType.DATE)

        assertEquals(java.time.LocalDate.now().minusYears(1), param.parse(null))
    }

    @Test
    fun `deprecated defaults to false`() {
        val param = ParamDef("pattern", "<regex>", "Pattern", flag = false, defaultValue = null, enhancePattern = false, type = ParamType.STRING)

        assertEquals(false, param.deprecated)
        assertEquals(null, param.deprecatedMessage)
    }

    @Test
    fun `deprecated param stores message`() {
        val param = ParamDef(
            "root-package", "<pkg>", "Old param", flag = false, defaultValue = null,
            enhancePattern = false, type = ParamType.STRING,
            deprecated = true, deprecatedMessage = "Use package-filter instead",
        )

        assertEquals(true, param.deprecated)
        assertEquals("Use package-filter instead", param.deprecatedMessage)
    }

    @Test
    fun `parseRequiredFrom throws when STRING property is missing`() {
        val param = ParamDef("pattern", "<regex>", "Pattern", flag = false, defaultValue = null, enhancePattern = false, type = ParamType.STRING)

        val exception = assertFailsWith<IllegalArgumentException> {
            param.parseRequiredFrom(emptyMap())
        }

        assertEquals("Missing required property 'pattern'", exception.message)
    }

    @Test
    fun `parseRequiredFrom returns value when STRING property is present`() {
        val param = ParamDef("pattern", "<regex>", "Pattern", flag = false, defaultValue = null, enhancePattern = false, type = ParamType.STRING)

        val result: String = param.parseRequiredFrom(mapOf("pattern" to "MyService"))

        assertEquals("MyService", result)
    }

    @Test
    fun `parseRequiredFrom returns default for BOOLEAN when property is absent`() {
        val param = ParamDef("project-only", "true", "Hide stdlib", flag = false, defaultValue = "true", enhancePattern = false, type = ParamType.BOOLEAN)

        val result: Boolean = param.parseRequiredFrom(emptyMap())

        assertEquals(true, result)
    }
}

class TaskDefTest {

    @Test
    fun `stores goal, description, params, and requiresCompilation`() {
        val pattern = ParamDef("pattern", "<regex>", "Class/symbol regex", flag = false, defaultValue = null, enhancePattern = false, type = ParamType.STRING)
        val task = TaskDef(
            goal = "find-class",
            description = "Find classes by regex",
            params = listOf(pattern),
            requiresCompilation = true,
            category = TaskCategory.NAVIGATION,
        )

        assertEquals("find-class", task.goal)
        assertEquals("Find classes by regex", task.description)
        assertEquals(listOf(pattern), task.params)
        assertEquals(true, task.requiresCompilation)
        assertEquals(TaskCategory.NAVIGATION, task.category)
    }

    @Test
    fun `enhanceProperties applies PatternEnhancer to params marked enhancePattern`() {
        val enhanced = ParamDef("pattern", "<regex>", "Pattern", flag = false, defaultValue = null, enhancePattern = true, type = ParamType.STRING)
        val plain = ParamDef("method", "<name>", "Method", flag = false, defaultValue = null, enhancePattern = false, type = ParamType.STRING)
        val task = TaskDef(
            goal = "test",
            description = "Test",
            params = listOf(enhanced, plain),
            requiresCompilation = false,
            category = TaskCategory.NAVIGATION,
        )

        val result = task.enhanceProperties(mapOf("pattern" to "MyService", "method" to "doStuff"))

        assertEquals("My.*Service", result["pattern"])
        assertEquals("doStuff", result["method"])
    }

    @Test
    fun `enhanceProperties leaves null values unchanged`() {
        val enhanced = ParamDef("pattern", "<regex>", "Pattern", flag = false, defaultValue = null, enhancePattern = true, type = ParamType.STRING)
        val task = TaskDef(
            goal = "test",
            description = "Test",
            params = listOf(enhanced),
            requiresCompilation = false,
            category = TaskCategory.NAVIGATION,
        )

        val result = task.enhanceProperties(mapOf("pattern" to null))

        assertEquals(null, result["pattern"])
    }

    @Test
    fun `enhanceProperties throws on unknown property key`() {
        val pattern = ParamDef("pattern", "<regex>", "Pattern", flag = false, defaultValue = null, enhancePattern = true, type = ParamType.STRING)
        val task = TaskDef(
            goal = "test",
            description = "Test",
            params = listOf(pattern),
            requiresCompilation = false,
            category = TaskCategory.NAVIGATION,
        )

        val exception = assertFailsWith<IllegalArgumentException> {
            task.enhanceProperties(mapOf("pattern" to "Foo", "typo-param" to "bar"))
        }

        assertTrue(exception.message!!.contains("typo-param"))
        assertTrue(exception.message!!.contains("test"))
    }

    @Test
    fun `resolves Gradle task name`() {
        val task = TaskDef(
            goal = "find-class",
            description = "Find classes by regex",
            params = emptyList(),
            requiresCompilation = true,
            category = TaskCategory.NAVIGATION,
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
            category = TaskCategory.NAVIGATION,
        )

        assertEquals("cnav:find-class", task.taskName(BuildTool.MAVEN))
    }

    @Test
    fun `goalToGradleTaskName derives cnav-prefixed camelCase from kebab-case goal`() {
        assertEquals("cnavFindClass", TaskDef.goalToGradleTaskName("find-class"))
        assertEquals("cnavListClasses", TaskDef.goalToGradleTaskName("list-classes"))
        assertEquals("cnavDsm", TaskDef.goalToGradleTaskName("dsm"))
        assertEquals("cnavFindStringConstant", TaskDef.goalToGradleTaskName("find-string-constant"))
        assertEquals("cnavAgentHelp", TaskDef.goalToGradleTaskName("agent-help"))
    }

    @Test
    fun `gradleTaskName property returns derived canonical name`() {
        val task = TaskDef(
            goal = "class-detail",
            description = "Test",
            params = emptyList(),
            requiresCompilation = true,
            category = TaskCategory.NAVIGATION,
            legacyGradleTaskName = "cnavClass",
        )

        assertEquals("cnavClassDetail", task.gradleTaskName)
    }

    @Test
    fun `legacyGradleTaskName is null by default`() {
        val task = TaskDef(
            goal = "find-class",
            description = "Test",
            params = emptyList(),
            requiresCompilation = true,
            category = TaskCategory.NAVIGATION,
        )

        assertEquals(null, task.legacyGradleTaskName)
    }

    @Test
    fun `legacyGradleTaskName stores irregular name when set`() {
        val task = TaskDef(
            goal = "class-detail",
            description = "Test",
            params = emptyList(),
            requiresCompilation = true,
            category = TaskCategory.NAVIGATION,
            legacyGradleTaskName = "cnavClass",
        )

        assertEquals("cnavClass", task.legacyGradleTaskName)
    }

    @Test
    fun `deprecations returns warning when deprecated param is present in properties`() {
        val deprecated = ParamDef(
            "old-param", "<v>", "Old", flag = false, defaultValue = null,
            enhancePattern = false, type = ParamType.STRING,
            deprecated = true, deprecatedMessage = "Use new-param instead",
        )
        val task = TaskDef(
            goal = "test-task", description = "Test", params = listOf(deprecated),
            requiresCompilation = false, category = TaskCategory.NAVIGATION,
        )

        val warnings = task.deprecations(mapOf("old-param" to "value"))

        assertEquals(listOf("Use new-param instead"), warnings)
    }

    @Test
    fun `deprecations returns empty when deprecated param is absent`() {
        val deprecated = ParamDef(
            "old-param", "<v>", "Old", flag = false, defaultValue = null,
            enhancePattern = false, type = ParamType.STRING,
            deprecated = true, deprecatedMessage = "Use new-param instead",
        )
        val task = TaskDef(
            goal = "test-task", description = "Test", params = listOf(deprecated),
            requiresCompilation = false, category = TaskCategory.NAVIGATION,
        )

        val warnings = task.deprecations(emptyMap())

        assertEquals(emptyList<String>(), warnings)
    }

    @Test
    fun `deprecations ignores non-deprecated params`() {
        val normal = ParamDef(
            "pattern", "<regex>", "Pattern", flag = false, defaultValue = null,
            enhancePattern = false, type = ParamType.STRING,
        )
        val task = TaskDef(
            goal = "test-task", description = "Test", params = listOf(normal),
            requiresCompilation = false, category = TaskCategory.NAVIGATION,
        )

        val warnings = task.deprecations(mapOf("pattern" to "value"))

        assertEquals(emptyList<String>(), warnings)
    }

    @Test
    fun `usageHint shows required params without brackets for Gradle`() {
        val pattern = ParamDef("pattern", "<regex>", "Pattern", flag = false, defaultValue = null, enhancePattern = false, type = ParamType.STRING)
        val task = TaskDef(
            goal = "find-class",
            description = "Find classes",
            params = listOf(pattern),
            requiresCompilation = true,
            category = TaskCategory.NAVIGATION,
        )

        val hint = task.usageHint(BuildTool.GRADLE)

        assertEquals("Usage: ./gradlew cnavFindClass -Ppattern=<regex>", hint)
    }

    @Test
    fun `usageHint shows optional params in brackets`() {
        val pattern = ParamDef("pattern", "<regex>", "Pattern", flag = false, defaultValue = null, enhancePattern = false, type = ParamType.STRING)
        val maxdepth = ParamDef("maxdepth", "<N>", "Max depth", flag = false, defaultValue = "3", enhancePattern = false, type = ParamType.INT)
        val task = TaskDef(
            goal = "find-callers",
            description = "Find callers",
            params = listOf(pattern, maxdepth),
            requiresCompilation = true,
            category = TaskCategory.NAVIGATION,
        )

        val hint = task.usageHint(BuildTool.GRADLE)

        assertEquals("Usage: ./gradlew cnavFindCallers -Ppattern=<regex> [-Pmaxdepth=<N>]", hint)
    }

    @Test
    fun `usageHint excludes format and llm params`() {
        val format = ParamDef("format", "<fmt>", "Output format", flag = false, defaultValue = null, enhancePattern = false, type = ParamType.STRING)
        val llm = ParamDef("llm", "true", "LLM mode", flag = false, defaultValue = null, enhancePattern = false, type = ParamType.BOOLEAN)
        val pattern = ParamDef("pattern", "<regex>", "Pattern", flag = false, defaultValue = null, enhancePattern = false, type = ParamType.STRING)
        val task = TaskDef(
            goal = "find-class",
            description = "Find classes",
            params = listOf(format, llm, pattern),
            requiresCompilation = true,
            category = TaskCategory.NAVIGATION,
        )

        val hint = task.usageHint(BuildTool.GRADLE)

        assertEquals("Usage: ./gradlew cnavFindClass -Ppattern=<regex>", hint)
    }

    @Test
    fun `usageHint excludes deprecated params`() {
        val pattern = ParamDef("pattern", "<regex>", "Pattern", flag = false, defaultValue = null, enhancePattern = false, type = ParamType.STRING)
        val old = ParamDef("old-param", "<v>", "Old", flag = false, defaultValue = null, enhancePattern = false, type = ParamType.STRING, deprecated = true, deprecatedMessage = "Use new instead")
        val task = TaskDef(
            goal = "find-class",
            description = "Find classes",
            params = listOf(pattern, old),
            requiresCompilation = true,
            category = TaskCategory.NAVIGATION,
        )

        val hint = task.usageHint(BuildTool.GRADLE)

        assertEquals("Usage: ./gradlew cnavFindClass -Ppattern=<regex>", hint)
    }

    @Test
    fun `usageHint works for Maven`() {
        val pattern = ParamDef("pattern", "<regex>", "Pattern", flag = false, defaultValue = null, enhancePattern = false, type = ParamType.STRING)
        val task = TaskDef(
            goal = "find-class",
            description = "Find classes",
            params = listOf(pattern),
            requiresCompilation = true,
            category = TaskCategory.NAVIGATION,
        )

        val hint = task.usageHint(BuildTool.MAVEN)

        assertEquals("Usage: mvn cnav:find-class -Dpattern=<regex>", hint)
    }

    @Test
    fun `usageHint for real FIND_CALLERS task uses canonical name`() {
        val hint = TaskRegistry.FIND_CALLERS.usageHint(BuildTool.GRADLE)

        assertTrue(hint.contains("cnavFindCallers"), "Should use canonical name, not legacy 'cnavCallers'")
        assertTrue(hint.contains("-Ppattern="), "Should include required pattern param")
    }

    @Test
    fun `usageHint for real CLASS_DETAIL task uses canonical name`() {
        val hint = TaskRegistry.CLASS_DETAIL.usageHint(BuildTool.GRADLE)

        assertTrue(hint.contains("cnavClassDetail"), "Should use canonical name, not legacy 'cnavClass'")
    }
}

class TaskRegistryTest {

    @Test
    fun `contains all 31 goals`() {
        val goals = TaskRegistry.ALL_TASKS.map { it.goal }.toSet()

        assertEquals(31, goals.size)
        assertTrue(goals.contains("find-class"))
        assertTrue(goals.contains("hotspots"))
        assertTrue(goals.contains("complexity"))
        assertTrue(goals.contains("metrics"))
        assertTrue(goals.contains("help"))
        assertTrue(goals.contains("cycles"))
        assertTrue(goals.contains("find-string-constant"))
        assertTrue(goals.contains("type-hierarchy"))
        assertTrue(goals.contains("annotations"))
        assertTrue(goals.contains("context"))
        assertTrue(goals.contains("distance"))
        assertTrue(goals.contains("strength"))
        assertTrue(goals.contains("volatility"))
    }

    @Test
    fun `every goal in GRADLE_TASK_NAMES has a matching TaskDef`() {
        val registryGoals = TaskRegistry.ALL_TASKS.map { it.goal }.toSet()

        for (task in TaskRegistry.ALL_TASKS) {
            val gradleName = task.taskName(BuildTool.GRADLE)
            assertNotNull(gradleName, "Goal '${task.goal}' should resolve to a Gradle task name")
        }
        assertEquals(31, registryGoals.size)
    }

    @Test
    fun `exactly 8 tasks have legacy Gradle task names`() {
        val legacyTasks = TaskRegistry.ALL_TASKS.filter { it.legacyGradleTaskName != null }

        assertEquals(8, legacyTasks.size)

        val legacyMap = legacyTasks.associate { it.goal to it.legacyGradleTaskName }
        assertEquals("cnavClass", legacyMap["class-detail"])
        assertEquals("cnavCallers", legacyMap["find-callers"])
        assertEquals("cnavCallees", legacyMap["find-callees"])
        assertEquals("cnavInterfaces", legacyMap["find-interfaces"])
        assertEquals("cnavDeps", legacyMap["package-deps"])
        assertEquals("cnavUsages", legacyMap["find-usages"])
        assertEquals("cnavAge", legacyMap["code-age"])
        assertEquals("cnavHelpConfig", legacyMap["config-help"])
    }

    @Test
    fun `all gradleTaskName values are unique across tasks`() {
        val names = TaskRegistry.ALL_TASKS.map { it.gradleTaskName }

        assertEquals(names.size, names.toSet().size, "Duplicate gradleTaskName found")
    }

    @Test
    fun `navigation tasks require compilation`() {
        val navigationGoals = listOf(
            "list-classes", "find-class", "find-symbol", "class-detail",
            "find-callers", "find-callees", "find-interfaces", "package-deps",
            "dsm", "find-usages", "rank", "dead", "type-hierarchy",
        )

        for (goal in navigationGoals) {
            val task = TaskRegistry.ALL_TASKS.first { it.goal == goal }
            assertTrue(task.requiresCompilation, "Navigation task '$goal' should require compilation")
        }
    }

    @Test
    fun `dead code task requires test compilation`() {
        assertTrue(TaskRegistry.DEAD.requiresTestCompilation, "Dead code task should require test compilation")
    }

    @Test
    fun `most tasks do not require test compilation`() {
        val nonTestGoals = listOf(
            "list-classes", "find-class", "find-symbol", "class-detail",
            "find-callers", "find-callees", "find-interfaces", "package-deps",
            "dsm", "find-usages", "rank", "type-hierarchy",
        )

        for (goal in nonTestGoals) {
            val task = TaskRegistry.ALL_TASKS.first { it.goal == goal }
            assertTrue(!task.requiresTestCompilation, "Task '$goal' should NOT require test compilation")
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
    fun `help tasks do not require compilation`() {
        val helpGoals = listOf("help", "agent-help", "config-help")

        for (goal in helpGoals) {
            val task = TaskRegistry.ALL_TASKS.first { it.goal == goal }
            assertTrue(!task.requiresCompilation, "Help task '$goal' should not require compilation")
        }
    }

    @Test
    fun `navigation tasks have category NAVIGATION`() {
        val navigationGoals = listOf(
            "list-classes", "find-class", "find-symbol", "class-detail",
            "find-callers", "find-callees", "find-interfaces", "package-deps",
            "dsm", "cycles", "find-usages", "rank", "dead", "type-hierarchy",
            "find-string-constant", "annotations", "complexity", "metrics", "context",
        )

        for (goal in navigationGoals) {
            val task = TaskRegistry.ALL_TASKS.first { it.goal == goal }
            assertEquals(TaskCategory.NAVIGATION, task.category, "Task '$goal' should have category NAVIGATION")
        }
    }

    @Test
    fun `git history tasks have category GIT_HISTORY`() {
        val gitGoals = listOf("hotspots", "churn", "code-age", "authors", "coupling")

        for (goal in gitGoals) {
            val task = TaskRegistry.ALL_TASKS.first { it.goal == goal }
            assertEquals(TaskCategory.GIT_HISTORY, task.category, "Task '$goal' should have category GIT_HISTORY")
        }
    }

    @Test
    fun `help tasks have category HELP`() {
        val helpGoals = listOf("help", "agent-help", "config-help")

        for (goal in helpGoals) {
            val task = TaskRegistry.ALL_TASKS.first { it.goal == goal }
            assertEquals(TaskCategory.HELP, task.category, "Task '$goal' should have category HELP")
        }
    }

    @Test
    fun `changed-since has category HYBRID`() {
        assertEquals(TaskCategory.HYBRID, TaskRegistry.CHANGED_SINCE.category)
    }

    @Test
    fun `every task has a category`() {
        for (task in TaskRegistry.ALL_TASKS) {
            assertNotNull(task.category, "Task '${task.goal}' should have a non-null category")
        }
    }

    @Test
    fun `help and config-help tasks have no params`() {
        val noParamGoals = listOf("help", "config-help")

        for (goal in noParamGoals) {
            val task = TaskRegistry.ALL_TASKS.first { it.goal == goal }
            assertTrue(task.params.isEmpty(), "Help task '$goal' should have no params")
        }
    }

    @Test
    fun `find-usages has owner-class, method, field, type, and outside-package params`() {
        val paramNames = TaskRegistry.FIND_USAGES.params.map { it.name }.toSet()

        assertTrue(paramNames.contains("owner-class"))
        assertTrue(paramNames.contains("method"))
        assertTrue(paramNames.contains("field"))
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
    fun `METRICS_TOP param has default value of 5`() {
        assertEquals("5", TaskRegistry.METRICS_TOP.defaultValue)
    }

    @Test
    fun `PROJECTONLY param defaults to true`() {
        assertEquals("true", TaskRegistry.PROJECTONLY.defaultValue)
    }

    @Test
    fun `complexity and rank have collapse-lambdas param`() {
        val complexityParams = TaskRegistry.COMPLEXITY.params.map { it.name }.toSet()
        val rankParams = TaskRegistry.RANK.params.map { it.name }.toSet()

        assertTrue(complexityParams.contains("collapse-lambdas"), "complexity should have collapse-lambdas param")
        assertTrue(rankParams.contains("collapse-lambdas"), "rank should have collapse-lambdas param")
    }

    @Test
    fun `collapse-lambdas param defaults to true`() {
        val param = TaskRegistry.COMPLEXITY.paramByName("collapse-lambdas")

        assertEquals("true", param.defaultValue)
        assertEquals(ParamType.BOOLEAN, param.type)
    }

    @Test
    fun `AFTER param has default value of 1 year ago`() {
        assertEquals("1 year ago", TaskRegistry.AFTER.defaultValue)
    }

    @Test
    fun `PATTERN param has no default value`() {
        assertEquals(null, TaskRegistry.PATTERN.defaultValue)
    }

    @Test
    fun `find-callers and find-callees have filter-synthetic param`() {
        val callersParams = TaskRegistry.FIND_CALLERS.params.map { it.name }.toSet()
        val calleesParams = TaskRegistry.FIND_CALLEES.params.map { it.name }.toSet()

        assertTrue(callersParams.contains("filter-synthetic"))
        assertTrue(calleesParams.contains("filter-synthetic"))
    }

    @Test
    fun `FILTER_SYNTHETIC param defaults to true`() {
        val param = TaskRegistry.FIND_CALLERS.paramByName("filter-synthetic")

        assertEquals("true", param.defaultValue)
    }

    @Test
    fun `warnUnsupportedProperties returns warning for known cnav param not in this task`() {
        val task = TaskRegistry.DEAD

        val warnings = task.warnUnsupportedProperties(setOf("test-only", "filter", "maxdepth"))

        assertEquals(listOf("Parameter 'maxdepth' is not supported by task 'dead'. Supported: [classes-only, exclude, exclude-annotated, filter, format, llm, prod-only, test-only, treat-as-dead]"), warnings)
    }

    @Test
    fun `warnUnsupportedProperties returns empty for supported params`() {
        val task = TaskRegistry.DEAD

        val warnings = task.warnUnsupportedProperties(setOf("test-only", "filter", "prod-only"))

        assertEquals(emptyList<String>(), warnings)
    }

    @Test
    fun `warnUnsupportedProperties ignores properties that are not known cnav params`() {
        val task = TaskRegistry.DEAD

        val warnings = task.warnUnsupportedProperties(setOf("filter", "some-random-gradle-prop"))

        assertEquals(emptyList<String>(), warnings)
    }

    @Test
    fun `warnUnsupportedProperties reports multiple unsupported params`() {
        val task = TaskRegistry.DEAD

        val warnings = task.warnUnsupportedProperties(setOf("maxdepth", "project-only"))

        assertEquals(2, warnings.size)
        assertTrue(warnings.any { it.contains("maxdepth") })
        assertTrue(warnings.any { it.contains("project-only") })
    }

    @Test
    fun `all ParamDef instances have correct ParamType`() {
        val expectedTypes = mapOf(
            "format" to ParamType.STRING,
            "llm" to ParamType.BOOLEAN,
            "pattern" to ParamType.STRING,
            "method" to ParamType.STRING,
            "maxdepth" to ParamType.INT,
            "project-only" to ParamType.BOOLEAN,
            "filter-synthetic" to ParamType.BOOLEAN,
            "top" to ParamType.INT,
            "after" to ParamType.DATE,
            "no-follow" to ParamType.FLAG,
            "min-revs" to ParamType.INT,
            "include-test" to ParamType.BOOLEAN,
            "package" to ParamType.STRING,
            "reverse" to ParamType.BOOLEAN,
            "root-package" to ParamType.STRING,
            "dsm-depth" to ParamType.INT,
            "dsm-html" to ParamType.STRING,
            "cycles" to ParamType.BOOLEAN,
            "cycle" to ParamType.STRING,
            "owner-class" to ParamType.STRING,
            "field" to ParamType.STRING,
            "type" to ParamType.STRING,
            "outside-package" to ParamType.STRING,
            "filter" to ParamType.STRING,
            "exclude" to ParamType.STRING,
            "classes-only" to ParamType.BOOLEAN,
            "exclude-annotated" to ParamType.LIST_STRING,
            "prod-only" to ParamType.BOOLEAN,
            "test-only" to ParamType.BOOLEAN,
            "detail" to ParamType.BOOLEAN,
            "min-shared-revs" to ParamType.INT,
            "min-coupling" to ParamType.INT,
            "max-changeset-size" to ParamType.INT,
            "collapse-lambdas" to ParamType.BOOLEAN,
            "section" to ParamType.STRING,
            "ref" to ParamType.STRING,
            "treat-as-dead" to ParamType.LIST_STRING,
            "methods" to ParamType.BOOLEAN,
            "package-filter" to ParamType.STRING,
            "include-external" to ParamType.BOOLEAN,
        )

        val allParams = TaskRegistry.ALL_TASKS.flatMap { it.params }.distinctBy { it.name }

        for (param in allParams) {
            val expected = expectedTypes[param.name]
            assertNotNull(expected, "No expected type for param '${param.name}' — add it to expectedTypes")
            assertEquals(expected, param.type, "Param '${param.name}' should have type $expected but was ${param.type}")
        }

        assertEquals(expectedTypes.size, allParams.size, "expectedTypes count should match actual param count")
    }
}
