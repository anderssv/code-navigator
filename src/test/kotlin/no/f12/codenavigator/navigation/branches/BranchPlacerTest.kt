package no.f12.codenavigator.navigation.branches

import no.f12.codenavigator.navigation.relations.callgraph.MethodRef
import no.f12.codenavigator.navigation.types.ClassName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Placing bytecode callees into a function's branches, on hand-built branch outlines. */
class BranchPlacerTest {

    private fun ref(cls: String, method: String) = MethodRef(ClassName("p.$cls"), method)
    private fun call(name: String, line: Int = 1) = CallSite(name, line)
    private fun prop(name: String, line: Int = 1) = CallSite(name, line, property = true)
    private fun branch(label: String, vararg children: BranchItem, kind: BranchKind = BranchKind.IF) = Branch(kind, label, 1, children.toList())
    private fun fn(vararg body: BranchItem, name: String = "f", cls: String = "p.Svc", start: Int = 1, end: Int = 20) =
        FunctionBranches(cls, name, start, end, body.toList())

    private val delete = ref("Repo", "delete")
    private val audit = ref("Audit", "log")

    private fun render(items: List<PlacedItem>, indent: String = ""): String = items.joinToString("\n") { item ->
        when (item) {
            is PlacedCall -> "$indent→ ${item.callee.className.value.substringAfterLast('.')}.${item.callee.methodName}"
            is PlacedExit -> "$indent${item.text}"
            is PlacedBranch -> "$indent${item.label}\n${render(item.children, "$indent  ")}"
        }
    }

    // --- Placement ---

    @Test
    fun `callees go where their call is, calls without a callee are dropped`() {
        val placed = BranchPlacer.place(
            fn(call("println"), branch("if (a)", call("delete")), call("log")),
            listOf(delete, audit),
        )

        assertEquals("if (a)\n  → Repo.delete\n→ Audit.log", render(placed.items))
        assertEquals(emptyList(), placed.unplaced)
    }

    @Test
    fun `lambda bodies are listed apart from callees with no matching call`() {
        val lambda = MethodRef(ClassName("p.Svc\$f\$1"), "invokeSuspend")
        val unknown = ref("Mapper", "map")

        val placed = BranchPlacer.place(fn(call("delete")), listOf(lambda, unknown, delete))

        assertEquals(listOf(unknown), placed.unplaced)
        assertEquals(listOf(lambda), placed.lambdas)
    }

    @Test
    fun `a callee with no matching call is reported as unplaced`() {
        val unknown = ref("Mapper", "map")

        val placed = BranchPlacer.place(fn(call("delete")), listOf(unknown, delete))

        assertEquals("→ Repo.delete", render(placed.items))
        assertEquals(listOf(unknown), placed.unplaced)
    }

    @Test
    fun `branches with nothing placed are dropped, branches with an exit are kept`() {
        val placed = BranchPlacer.place(
            fn(
                branch("if (a)", call("println")),
                branch("if (b)", Exit(ExitKind.RETURN, "return", 1)),
                call("delete"),
            ),
            listOf(delete),
        )

        assertEquals("if (b)\n  return\n→ Repo.delete", render(placed.items))
    }

    @Test
    fun `a when keeps only the entries with something placed`() {
        val placed = BranchPlacer.place(
            fn(
                branch(
                    "when (type)",
                    branch("A ->", call("println"), kind = BranchKind.WHEN_ENTRY),
                    branch("B ->", call("delete"), kind = BranchKind.WHEN_ENTRY),
                    kind = BranchKind.WHEN,
                ),
            ),
            listOf(delete),
        )

        assertEquals("when (type)\n  B ->\n    → Repo.delete", render(placed.items))
    }

    @Test
    fun `a callee called in two branches appears under both`() {
        val placed = BranchPlacer.place(
            fn(branch("if (a)", call("delete")), branch("else", call("delete"), kind = BranchKind.ELSE)),
            listOf(delete),
        )

        assertEquals("if (a)\n  → Repo.delete\nelse\n  → Repo.delete", render(placed.items))
    }

    @Test
    fun `a callee called twice in the same list appears once there`() {
        val placed = BranchPlacer.place(fn(call("delete"), call("delete")), listOf(delete))

        assertEquals("→ Repo.delete", render(placed.items))
    }

    @Test
    fun `two callees with the same name are both placed at the call - matching is by name`() {
        val cacheDelete = ref("Cache", "delete")

        val placed = BranchPlacer.place(fn(branch("if (a)", call("delete"))), listOf(delete, cacheDelete))

        assertEquals("if (a)\n  → Cache.delete\n  → Repo.delete", render(placed.items))
    }

    @Test
    fun `nested branches keep their nesting`() {
        val placed = BranchPlacer.place(
            fn(branch("try", branch("if (a)", call("delete"), call("log")), kind = BranchKind.TRY)),
            listOf(delete, audit),
        )

        assertEquals("try\n  if (a)\n    → Repo.delete\n    → Audit.log", render(placed.items))
    }

    // --- Name matching ---

    @Test
    fun `property sites match getters and setters`() {
        val getName = ref("User", "getName")
        val setName = ref("User", "setName")
        val isAdmin = ref("User", "isAdmin")
        val setAdmin = ref("User", "setAdmin")

        val placed = BranchPlacer.place(
            fn(branch("if (a)", prop("name")), branch("if (b)", prop("isAdmin"))),
            listOf(getName, setName, isAdmin, setAdmin),
        )

        assertEquals(
            "if (a)\n  → User.getName\n  → User.setName\nif (b)\n  → User.isAdmin\n  → User.setAdmin",
            render(placed.items),
        )
    }

    @Test
    fun `a plain call does not match a getter`() {
        val placed = BranchPlacer.place(fn(call("name")), listOf(ref("User", "getName")))

        assertEquals("", render(placed.items))
        assertEquals(1, placed.unplaced.size)
    }

    @Test
    fun `constructor calls match the class's init, nested classes included`() {
        val order = MethodRef(ClassName("p.Order"), "<init>")
        val line = MethodRef(ClassName("p.Order\$Line"), "<init>")

        val placed = BranchPlacer.place(fn(branch("if (a)", call("Order")), call("Line")), listOf(order, line))

        assertEquals("if (a)\n  → Order.<init>\n→ Order\$Line.<init>", render(placed.items))
    }

    // --- Finding the function for a bytecode method ---

    private val functions = listOf(
        fn(name = "f", start = 3, end = 8),
        fn(name = "f", start = 10, end = 15),
        fn(name = "g", start = 17, end = 20),
        fn(name = "f", cls = "p.Other", start = 3, end = 8),
    )

    @Test
    fun `function by class and name, overloads told apart by line`() {
        assertEquals(10, BranchPlacer.functionFor(ref("Svc", "f"), firstLine = 12, functions)!!.startLine)
        assertEquals(3, BranchPlacer.functionFor(ref("Svc", "f"), firstLine = 4, functions)!!.startLine)
        assertEquals(17, BranchPlacer.functionFor(ref("Svc", "g"), firstLine = null, functions)!!.startLine)
    }

    @Test
    fun `lambda bodies and default-argument bridges map to their function`() {
        assertEquals("g", BranchPlacer.functionFor(ref("Svc", "g\$lambda\$0"), firstLine = 18, functions)!!.name)
        assertEquals("g", BranchPlacer.functionFor(ref("Svc", "g\$default"), firstLine = null, functions)!!.name)
    }

    @Test
    fun `methods of a lambda or object class map to the function that writes it`() {
        val nested = listOf(fn(name = "removeAll", start = 3, end = 8), fn(name = "run", cls = "p.Svc\$Inner", start = 10, end = 12))

        assertEquals("removeAll", BranchPlacer.functionFor(MethodRef(ClassName("p.Svc\$removeAll\$2"), "invokeSuspend"), firstLine = 5, nested)!!.name)
        assertEquals("removeAll", BranchPlacer.functionFor(MethodRef(ClassName("p.Svc\$removeAll\$2\$1"), "invoke"), firstLine = 5, nested)!!.name)
        assertEquals("run", BranchPlacer.functionFor(MethodRef(ClassName("p.Svc\$Inner\$run\$1"), "invoke"), firstLine = 11, nested)!!.name)
    }

    @Test
    fun `no function for an unknown class or name`() {
        assertNull(BranchPlacer.functionFor(ref("Nope", "f"), firstLine = 4, functions))
        assertNull(BranchPlacer.functionFor(ref("Svc", "h"), firstLine = 4, functions))
    }

    // --- Conditions for one callee (used by call-paths) ---

    @Test
    fun `conditions are the branch labels from the outside in, one list per call`() {
        val function = fn(
            branch("try", branch("if (a)", call("delete")), kind = BranchKind.TRY),
            branch("else", call("delete"), kind = BranchKind.ELSE),
            call("log"),
        )

        assertEquals(listOf(listOf("try", "if (a)"), listOf("else")), BranchPlacer.conditionsFor(function, delete))
        assertEquals(listOf(emptyList()), BranchPlacer.conditionsFor(function, audit))
    }

    @Test
    fun `conditions for a callee with no matching call are unknown`() {
        assertNull(BranchPlacer.conditionsFor(fn(call("x")), delete))
    }

    @Test
    fun `when entries show both the when and the entry`() {
        val function = fn(branch("when (type)", branch("B ->", call("delete"), kind = BranchKind.WHEN_ENTRY), kind = BranchKind.WHEN))

        assertEquals(listOf(listOf("when (type)", "B ->")), BranchPlacer.conditionsFor(function, delete))
    }
}
