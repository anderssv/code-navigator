package no.f12.codenavigator.navigation.relations.callgraph

import no.f12.codenavigator.navigation.types.ClassName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Formatter on hand-built results: every result shape in every output format. */
class CallPathFormatterTest {

    private fun ref(cls: String, method: String) = MethodRef(ClassName("com.example.$cls"), method)
    private fun step(cls: String, method: String, file: String? = "$cls.kt", line: Int? = 10, via: String? = null) =
        PathStep(ref(cls, method), via?.let { ClassName("com.example.$it") }, file, line)

    private val controller = ref("Controller", "delete")
    private val repo = ref("Repo", "delete")
    private val directPath = listOf(step("Controller", "delete", line = 10), step("Service", "delete", line = 5), step("Repo", "delete", line = 3))
    private val purgePath = listOf(step("Controller", "deleteOrPurge", line = 20), step("Purger", "purge", line = 12), step("Repo", "delete", line = 3))

    private fun report(
        reaching: List<EntryPaths> = emptyList(),
        guarded: List<MethodRef> = emptyList(),
        guard: String? = null,
        truncated: Boolean = false,
        entryCount: Int = 2,
        targets: List<MethodRef> = listOf(repo),
    ) = CallPathReport(CallPathResult(entryCount, reaching, guarded, truncated), targets, guard, maxDepth = 8)

    // --- Paths, no guard ---

    private val noGuard = report(reaching = listOf(EntryPaths(controller, listOf(directPath), morePaths = false)))

    @Test
    fun `text - paths without a guard`() {
        assertEquals(
            """
            Call paths to com.example.Repo.delete (2 entry points searched, maxdepth 8)

            com.example.Controller.delete (Controller.kt:10)
              → com.example.Service.delete (Service.kt:5)
              → com.example.Repo.delete (Repo.kt:3)
            """.trimIndent(),
            CallPathFormatter.formatText(noGuard),
        )
    }

    @Test
    fun `json - paths without a guard`() {
        assertEquals(
            """{"targets":["com.example.Repo.delete"],"maxDepth":8,"entryCount":2,"entries":[{"entry":"com.example.Controller.delete","morePaths":false,"paths":[[""" +
                """{"method":"com.example.Controller.delete","sourceFile":"Controller.kt","lineNumber":10},""" +
                """{"method":"com.example.Service.delete","sourceFile":"Service.kt","lineNumber":5},""" +
                """{"method":"com.example.Repo.delete","sourceFile":"Repo.kt","lineNumber":3}]]}],"guardedEntries":[],"truncated":false,"notes":[]}""",
            CallPathFormatter.formatJson(noGuard),
        )
    }

    @Test
    fun `llm - paths without a guard`() {
        assertEquals(
            """
            call-paths to=com.example.Repo.delete entries=2 maxdepth=8
            com.example.Controller.delete Controller.kt:10 → com.example.Service.delete Service.kt:5 → com.example.Repo.delete Repo.kt:3
            """.trimIndent(),
            CallPathFormatter.formatLlm(noGuard),
        )
    }

    // --- Guard: some unguarded, some guarded ---

    private val mixed = report(
        reaching = listOf(EntryPaths(ref("Controller", "deleteOrPurge"), listOf(purgePath), morePaths = false)),
        guarded = listOf(controller),
        guard = "authorize",
        entryCount = 3,
    )

    private val guardNote = "The guard check ignores call order and conditions: a method that calls the guard anywhere counts as guarded."

    @Test
    fun `text - guard with unguarded and guarded entries`() {
        assertEquals(
            """
            Unguarded call paths to com.example.Repo.delete (guard: authorize, 3 entry points searched, maxdepth 8)

            com.example.Controller.deleteOrPurge (Controller.kt:20)
              → com.example.Purger.purge (Purger.kt:12)
              → com.example.Repo.delete (Repo.kt:3)

            Guarded on every path (1):
              com.example.Controller.delete

            Note: $guardNote
            """.trimIndent(),
            CallPathFormatter.formatText(mixed),
        )
    }

    @Test
    fun `json - guard with unguarded and guarded entries`() {
        assertEquals(
            """{"targets":["com.example.Repo.delete"],"guard":"authorize","maxDepth":8,"entryCount":3,"entries":[{"entry":"com.example.Controller.deleteOrPurge","morePaths":false,"paths":[[""" +
                """{"method":"com.example.Controller.deleteOrPurge","sourceFile":"Controller.kt","lineNumber":20},""" +
                """{"method":"com.example.Purger.purge","sourceFile":"Purger.kt","lineNumber":12},""" +
                """{"method":"com.example.Repo.delete","sourceFile":"Repo.kt","lineNumber":3}]]}],""" +
                """"guardedEntries":["com.example.Controller.delete"],"truncated":false,"notes":["$guardNote"]}""",
            CallPathFormatter.formatJson(mixed),
        )
    }

    @Test
    fun `llm - guard with unguarded and guarded entries`() {
        assertEquals(
            """
            call-paths to=com.example.Repo.delete guard=authorize entries=3 maxdepth=8
            UNGUARDED com.example.Controller.deleteOrPurge Controller.kt:20 → com.example.Purger.purge Purger.kt:12 → com.example.Repo.delete Repo.kt:3
            GUARDED com.example.Controller.delete
            note: $guardNote
            """.trimIndent(),
            CallPathFormatter.formatLlm(mixed),
        )
    }

    // --- Guard: everything guarded ---

    private val allGuarded = report(guarded = listOf(controller), guard = "authorize")

    @Test
    fun `text - guard with every path guarded`() {
        assertEquals(
            """
            No unguarded call path to com.example.Repo.delete (guard: authorize, 2 entry points searched, maxdepth 8)

            Guarded on every path (1):
              com.example.Controller.delete

            Note: $guardNote
            """.trimIndent(),
            CallPathFormatter.formatText(allGuarded),
        )
    }

    @Test
    fun `json - guard with every path guarded`() {
        assertEquals(
            """{"targets":["com.example.Repo.delete"],"guard":"authorize","maxDepth":8,"entryCount":2,"entries":[],""" +
                """"guardedEntries":["com.example.Controller.delete"],"truncated":false,"notes":["$guardNote"]}""",
            CallPathFormatter.formatJson(allGuarded),
        )
    }

    @Test
    fun `llm - guard with every path guarded`() {
        assertEquals(
            """
            call-paths to=com.example.Repo.delete guard=authorize entries=2 maxdepth=8
            (no unguarded paths)
            GUARDED com.example.Controller.delete
            note: $guardNote
            """.trimIndent(),
            CallPathFormatter.formatLlm(allGuarded),
        )
    }

    // --- No path at all ---

    private val noPathNote = "No path is not proof: reflection, dependency-injection wiring and coroutine bodies can hide calls from the call graph."

    @Test
    fun `text - no path without a guard`() {
        assertEquals(
            """
            No call path to com.example.Repo.delete (2 entry points searched, maxdepth 8)

            Note: $noPathNote
            """.trimIndent(),
            CallPathFormatter.formatText(report()),
        )
    }

    @Test
    fun `text - no path with a guard says no path, not no unguarded path`() {
        assertEquals(
            """
            No call path to com.example.Repo.delete (guard: authorize, 2 entry points searched, maxdepth 8)

            Note: $noPathNote
            """.trimIndent(),
            CallPathFormatter.formatText(report(guard = "authorize")),
        )
    }

    @Test
    fun `json - no path`() {
        assertEquals(
            """{"targets":["com.example.Repo.delete"],"maxDepth":8,"entryCount":2,"entries":[],"guardedEntries":[],"truncated":false,"notes":["$noPathNote"]}""",
            CallPathFormatter.formatJson(report()),
        )
    }

    @Test
    fun `llm - no path`() {
        assertEquals(
            """
            call-paths to=com.example.Repo.delete entries=2 maxdepth=8
            (no paths)
            note: $noPathNote
            """.trimIndent(),
            CallPathFormatter.formatLlm(report()),
        )
    }

    // --- Step details: interface dispatch, library methods, more paths, truncation, several targets ---

    private val details = report(
        reaching = listOf(
            EntryPaths(
                controller,
                listOf(
                    listOf(step("Controller", "delete"), step("UnsafeAdapter", "delete", file = "Dispatch.kt", line = 18, via = "Port"), step("Jdbc", "update", file = null, line = null)),
                    listOf(step("Controller", "delete"), step("Jdbc", "update", file = null, line = null)),
                ),
                morePaths = true,
            ),
        ),
        truncated = true,
        targets = listOf(ref("Jdbc", "update"), ref("Jdbc", "batch"), ref("Jdbc", "exec"), ref("Jdbc", "call")),
    )

    private val truncatedNote = "More entry points reach the target than are listed; raise top to see them."

    @Test
    fun `text - interface step, library step, more paths, truncation and several targets`() {
        assertEquals(
            """
            Call paths to com.example.Jdbc.update, com.example.Jdbc.batch, com.example.Jdbc.exec +1 more (2 entry points searched, maxdepth 8)

            com.example.Controller.delete (Controller.kt:10)
              → com.example.UnsafeAdapter.delete (Dispatch.kt:18) via com.example.Port
              → com.example.Jdbc.update

            com.example.Controller.delete (Controller.kt:10)
              → com.example.Jdbc.update
              … more paths from this entry point not shown

            Note: $truncatedNote
            """.trimIndent(),
            CallPathFormatter.formatText(details),
        )
    }

    @Test
    fun `json - interface step, library step, more paths, truncation and several targets`() {
        assertEquals(
            """{"targets":["com.example.Jdbc.update","com.example.Jdbc.batch","com.example.Jdbc.exec","com.example.Jdbc.call"],"maxDepth":8,"entryCount":2,""" +
                """"entries":[{"entry":"com.example.Controller.delete","morePaths":true,"paths":[""" +
                """[{"method":"com.example.Controller.delete","sourceFile":"Controller.kt","lineNumber":10},""" +
                """{"method":"com.example.UnsafeAdapter.delete","sourceFile":"Dispatch.kt","lineNumber":18,"via":"com.example.Port"},""" +
                """{"method":"com.example.Jdbc.update"}],""" +
                """[{"method":"com.example.Controller.delete","sourceFile":"Controller.kt","lineNumber":10},{"method":"com.example.Jdbc.update"}]]}],""" +
                """"guardedEntries":[],"truncated":true,"notes":["$truncatedNote"]}""",
            CallPathFormatter.formatJson(details),
        )
    }

    @Test
    fun `llm - interface step, library step, more paths, truncation and several targets`() {
        assertEquals(
            """
            call-paths to=com.example.Jdbc.update,com.example.Jdbc.batch,com.example.Jdbc.exec,com.example.Jdbc.call entries=2 maxdepth=8
            com.example.Controller.delete Controller.kt:10 → com.example.UnsafeAdapter.delete Dispatch.kt:18 [via com.example.Port] → com.example.Jdbc.update
            com.example.Controller.delete Controller.kt:10 → com.example.Jdbc.update
            (more paths from com.example.Controller.delete not shown)
            note: $truncatedNote
            """.trimIndent(),
            CallPathFormatter.formatLlm(details),
        )
    }

    // --- Conditions on steps (branches) ---

    private val withConditions = report(
        reaching = listOf(
            EntryPaths(
                ref("Controller", "remove"),
                listOf(
                    listOf(
                        step("Controller", "remove"),
                        step("Service", "remove").copy(conditions = listOf(listOf("try"))),
                        step("Repo", "archive").copy(conditions = listOf(listOf("when (hard)", "false ->"), emptyList())),
                        step("Db", "exec", file = null, line = null).copy(conditions = null),
                    ),
                ),
                morePaths = false,
            ),
        ),
        targets = listOf(ref("Db", "exec")),
    )

    @Test
    fun `text - each step shows the conditions it is called under`() {
        assertEquals(
            """
            Call paths to com.example.Db.exec (2 entry points searched, maxdepth 8)

            com.example.Controller.remove (Controller.kt:10)
              → com.example.Service.remove (Service.kt:10)  [try]
              → com.example.Repo.archive (Repo.kt:10)  [when (hard) › false -> | always]
              → com.example.Db.exec
            """.trimIndent(),
            CallPathFormatter.formatText(withConditions),
        )
    }

    @Test
    fun `llm - conditions in brackets after each step`() {
        assertEquals(
            """
            call-paths to=com.example.Db.exec entries=2 maxdepth=8
            com.example.Controller.remove Controller.kt:10 → com.example.Service.remove Service.kt:10 [try] → com.example.Repo.archive Repo.kt:10 [when (hard) › false -> | always] → com.example.Db.exec
            """.trimIndent(),
            CallPathFormatter.formatLlm(withConditions),
        )
    }

    @Test
    fun `json - conditions as nested lists, left out when unknown`() {
        val json = CallPathFormatter.formatJson(withConditions)

        assertTrue(json.contains(""""method":"com.example.Service.remove","sourceFile":"Service.kt","lineNumber":10,"conditions":[["try"]]}"""), json)
        assertTrue(json.contains(""""method":"com.example.Repo.archive","sourceFile":"Repo.kt","lineNumber":10,"conditions":[["when (hard)","false ->"],[]]}"""), json)
        assertTrue(json.contains("""{"method":"com.example.Db.exec"}"""), json)
    }

    @Test
    fun `interface step shows via before the conditions`() {
        val text = CallPathFormatter.formatText(
            report(
                reaching = listOf(
                    EntryPaths(
                        controller,
                        listOf(listOf(step("Controller", "delete"), step("Adapter", "delete", via = "Port").copy(conditions = listOf(listOf("if (a)"))))),
                        morePaths = false,
                    ),
                ),
            ),
        )

        assertTrue(text.contains("  → com.example.Adapter.delete (Adapter.kt:10) via com.example.Port  [if (a)]"), text)
    }
}
