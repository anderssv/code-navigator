package no.f12.codenavigator.registry

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TaskDocsTest {

    private val documented = TaskRegistry.ALL_TASKS.filter { it.category != TaskCategory.HELP }

    @Test
    fun `every non-help task has a doc with a question and a schema`() {
        for (task in documented) {
            assertTrue(task.doc.whenToUse.isNotEmpty(), "${task.goal} needs at least one whenToUse question")
            assertTrue(task.doc.schema.isNotEmpty(), "${task.goal} needs a JSON schema example")
            assertTrue(task.doc.details.isNotEmpty(), "${task.goal} needs a details paragraph")
        }
    }

    @Test
    fun `no doc entry is orphaned and all related goals exist`() {
        val goals = TaskRegistry.ALL_TASKS.map { it.goal }.toSet()
        for ((goal, doc) in TaskDocs.byGoal) {
            assertTrue(goal in goals, "TaskDocs has an entry for unknown goal '$goal'")
            val goalsAndAliases = goals + TaskRegistry.ALL_TASKS.flatMap { it.aliases }
            for (r in doc.related) assertTrue(r in goals, "$goal relates to unknown goal '$r'")
            val templates = doc.details + doc.whenToUse + doc.interpretation + doc.schema + doc.caveats
            for (t in templates) for (ref in DocTemplate.referencedGoals(t)) {
                assertTrue(ref in goalsAndAliases, "$goal references unknown goal '{t:$ref}' in: $t")
            }
        }
    }

    @Test
    fun `templates render for both build tools without leaving placeholders`() {
        for (task in documented) for (tool in BuildTool.entries) {
            val doc = task.doc
            for (t in doc.details + doc.whenToUse + doc.interpretation + doc.schema + doc.caveats) {
                val rendered = DocTemplate.render(t, tool)
                assertTrue(!Regex("\\{(cmd|t|u|p|pf)[:}]").containsMatchIn(rendered), "unrendered placeholder in ${task.goal}: $rendered")
            }
        }
    }

    @Test
    fun `placeholders render per build tool`() {
        assertEquals("./gradlew cnavFindUsages --type=X --preview", DocTemplate.render("{u:find-usages} {p:type=X} {pf:preview}", BuildTool.GRADLE))
        assertEquals("mvn cnav:find-usages -Dtype=X -Dpreview", DocTemplate.render("{u:find-usages} {p:type=X} {pf:preview}", BuildTool.MAVEN))
        assertEquals("cnavRings and mvn", DocTemplate.render("{t:rings} and {cmd}", BuildTool.MAVEN).replace("cnav:rings", "cnavRings"))
        assertEquals(setOf("rings", "cycles"), DocTemplate.referencedGoals("{t:rings} {u:cycles} {p:scope=prod}"))
    }

    @Test
    fun `mutation tasks and only mutation tasks are refactoring tasks`() {
        assertEquals(TaskRegistry.ALL_TASKS.filter { it.kind == TaskKind.MUTATION }, TaskRegistry.REFACTORING_TASKS)
        assertEquals(TaskKind.QUERY, TaskRegistry.FIND_DECLARATION.kind)
        assertEquals(TaskKind.MUTATION, TaskRegistry.SAFE_DELETE_TASK.kind)
    }
}
