package no.f12.codenavigator.navigation.relations.callgraph

import no.f12.codenavigator.navigation.branches.BranchKind
import no.f12.codenavigator.navigation.branches.ExitKind
import no.f12.codenavigator.navigation.branches.PlacedBody
import no.f12.codenavigator.navigation.branches.PlacedBranch
import no.f12.codenavigator.navigation.branches.PlacedCall
import no.f12.codenavigator.navigation.branches.PlacedExit
import no.f12.codenavigator.navigation.types.ClassName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** find-callees output with branches, on hand-built call-tree nodes. */
class CallTreeBranchesFormatterTest {

    private fun ref(cls: String, method: String) = MethodRef(ClassName("p.$cls"), method)
    private fun node(method: MethodRef, file: String, line: Int, children: List<CallTreeNode> = emptyList(), branches: PlacedBody? = null) =
        CallTreeNode(method, file, line, children, branches = branches)

    private val canDelete = ref("Auth", "canDelete")
    private val delete = ref("Repo", "delete")
    private val archive = ref("Repo", "archive")
    private val exec = ref("Db", "exec")
    private val log = ref("Audit", "log")

    /** remove: canDelete, if (!ok) throw, when(hard) delete/archive; delete has its own branch; log is unplaced. */
    private val tree = node(
        ref("Service", "remove"), "Service.kt", 10,
        children = listOf(
            node(log, "Audit.kt", 3),
            node(canDelete, "Auth.kt", 5),
            node(
                delete, "Repo.kt", 4,
                children = listOf(node(exec, "Db.kt", 2)),
                branches = PlacedBody(listOf(PlacedBranch(BranchKind.IF, "if (id > 0)", 5, listOf(PlacedCall(exec, 5)))), emptyList()),
            ),
            node(archive, "Repo.kt", 8),
        ),
        branches = PlacedBody(
            items = listOf(
                PlacedCall(canDelete, 11),
                PlacedBranch(BranchKind.IF, "if (!ok)", 12, listOf(PlacedExit(ExitKind.THROW, "throw Forbidden()", 13))),
                PlacedBranch(
                    BranchKind.WHEN, "when (hard)", 15,
                    listOf(
                        PlacedBranch(BranchKind.WHEN_ENTRY, "true ->", 16, listOf(PlacedCall(delete, 16))),
                        PlacedBranch(BranchKind.WHEN_ENTRY, "false ->", 17, listOf(PlacedCall(archive, 17), PlacedCall(delete, 17))),
                    ),
                ),
            ),
            unplaced = listOf(log),
        ),
    )

    @Test
    fun `text - children are printed inside their branches, nested nodes with their own branches`() {
        assertEquals(
            """
            p.Service.remove
              → p.Auth.canDelete (Auth.kt:5)
              if (!ok)  :12
                throw Forbidden()  :13
              when (hard)  :15
                true ->  :16
                  → p.Repo.delete (Repo.kt:4)
                    if (id > 0)  :5
                      → p.Db.exec (Db.kt:2)
                false ->  :17
                  → p.Repo.archive (Repo.kt:8)
                  → p.Repo.delete (Repo.kt:4) (see above)
              (branch not known)
                → p.Audit.log (Audit.kt:3)
            """.trimIndent(),
            CallTreeFormatter.renderTrees(listOf(tree), CallDirection.CALLEES),
        )
    }

    @Test
    fun `llm - same layout`() {
        assertEquals(
            """
            p.Service.remove Service.kt:10
              → p.Auth.canDelete Auth.kt:5
              if (!ok) L12
                throw Forbidden() L13
              when (hard) L15
                true -> L16
                  → p.Repo.delete Repo.kt:4
                    if (id > 0) L5
                      → p.Db.exec Db.kt:2
                false -> L17
                  → p.Repo.archive Repo.kt:8
                  → p.Repo.delete Repo.kt:4 (see above)
              (branch not known)
                → p.Audit.log Audit.kt:3
            """.trimIndent(),
            CallTreeFormatter.formatLlm(listOf(tree), CallDirection.CALLEES),
        )
    }

    @Test
    fun `json - nodes carry their branches next to their children`() {
        val json = CallTreeFormatter.formatJson(listOf(tree), CallDirection.CALLEES)

        assertTrue(
            json.contains(""""branches":{"items":[{"call":"p.Auth.canDelete","line":11},{"branch":"if (!ok)","kind":"IF","line":12,"items":[{"exit":"throw Forbidden()","kind":"THROW","line":13}]}"""),
            json,
        )
        assertTrue(json.contains(""""unplaced":["p.Audit.log"],"lambdas":[]}"""), json)
        assertTrue(json.contains(""""method":"p.Repo.delete","sourceFile":"Repo.kt","lineNumber":4,"children":[{"method":"p.Db.exec""""), json)
        assertTrue(json.contains(""""branches":{"items":[{"branch":"if (id > 0)""""), json)
    }

    @Test
    fun `nodes without branches print as before`() {
        val plain = node(ref("Service", "remove"), "Service.kt", 10, children = listOf(node(log, "Audit.kt", 3)))

        assertEquals("p.Service.remove\n  → p.Audit.log (Audit.kt:3)", CallTreeFormatter.renderTrees(listOf(plain), CallDirection.CALLEES))
    }
}
