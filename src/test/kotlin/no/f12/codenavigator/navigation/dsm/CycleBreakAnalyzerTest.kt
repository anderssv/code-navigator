package no.f12.codenavigator.navigation.dsm

import no.f12.codenavigator.navigation.types.ClassName
import no.f12.codenavigator.navigation.types.PackageName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CycleBreakAnalyzerTest {

    // [TEST] Simple A↔B cycle: removing either direction breaks the cycle
    // [TEST] Triangle A→B→C→A: each edge is a break point
    // [TEST] Diamond with redundant path: one edge is not a break point
    // [TEST] Edge weight comes from class edge count
    // [TEST] Ranked by weight ascending (lightest = easiest to break)
    // [TEST] Break score: edge whose removal splits the SCC scores higher

    @Test
    fun `simple bidirectional cycle - both edges are break points`() {
        val cycle = CycleDetail(
            packages = listOf(PackageName("a"), PackageName("b")),
            edges = listOf(
                CycleEdge(PackageName("a"), PackageName("b"), setOf(ClassName("a.X") to ClassName("b.Y"))),
                CycleEdge(PackageName("b"), PackageName("a"), setOf(ClassName("b.Y") to ClassName("a.X"), ClassName("b.Z") to ClassName("a.X"))),
            ),
        )

        val ranked = CycleBreakAnalyzer.rankEdges(cycle)

        assertEquals(2, ranked.size)
        // a→b has weight 1 (1 class edge), b→a has weight 2 (2 class edges)
        assertEquals(PackageName("a"), ranked[0].from)
        assertEquals(PackageName("b"), ranked[0].to)
        assertEquals(1, ranked[0].weight)
        assertTrue(ranked[0].breaksycle)

        assertEquals(PackageName("b"), ranked[1].from)
        assertEquals(PackageName("a"), ranked[1].to)
        assertEquals(2, ranked[1].weight)
        assertTrue(ranked[1].breaksycle)
    }

    @Test
    fun `triangle cycle - all edges are break points`() {
        val cycle = CycleDetail(
            packages = listOf(PackageName("a"), PackageName("b"), PackageName("c")),
            edges = listOf(
                CycleEdge(PackageName("a"), PackageName("b"), setOf(ClassName("a.X") to ClassName("b.Y"))),
                CycleEdge(PackageName("b"), PackageName("c"), setOf(ClassName("b.Y") to ClassName("c.Z"))),
                CycleEdge(PackageName("c"), PackageName("a"), setOf(ClassName("c.Z") to ClassName("a.X"))),
            ),
        )

        val ranked = CycleBreakAnalyzer.rankEdges(cycle)

        assertEquals(3, ranked.size)
        assertTrue(ranked.all { it.breaksycle })
    }

    @Test
    fun `redundant edge is not a break point`() {
        // a→b, a→c, c→b, b→a — removing a→b doesn't break cycle (a→c→b→a still works)
        val cycle = CycleDetail(
            packages = listOf(PackageName("a"), PackageName("b"), PackageName("c")),
            edges = listOf(
                CycleEdge(PackageName("a"), PackageName("b"), setOf(ClassName("a.X") to ClassName("b.Y"))),
                CycleEdge(PackageName("a"), PackageName("c"), setOf(ClassName("a.X") to ClassName("c.Z"))),
                CycleEdge(PackageName("c"), PackageName("b"), setOf(ClassName("c.Z") to ClassName("b.Y"))),
                CycleEdge(PackageName("b"), PackageName("a"), setOf(ClassName("b.Y") to ClassName("a.X"))),
            ),
        )

        val ranked = CycleBreakAnalyzer.rankEdges(cycle)

        // Removing a→b: still have a→c→b→a — full SCC intact
        val aToB = ranked.find { it.from == PackageName("a") && it.to == PackageName("b") }!!
        assertTrue(!aToB.breaksycle, "a→b is redundant (a→c→b exists), removing it doesn't break cycle")

        // Removing b→a: no path back to a, SCC splits
        val bToA = ranked.find { it.from == PackageName("b") && it.to == PackageName("a") }!!
        assertTrue(bToA.breaksycle, "b→a is the only path back, removing it breaks cycle")
    }

    @Test
    fun `edges ranked by weight ascending`() {
        val cycle = CycleDetail(
            packages = listOf(PackageName("a"), PackageName("b")),
            edges = listOf(
                CycleEdge(PackageName("b"), PackageName("a"), setOf(
                    ClassName("b.Y") to ClassName("a.X"),
                    ClassName("b.Z") to ClassName("a.X"),
                    ClassName("b.W") to ClassName("a.X"),
                )),
                CycleEdge(PackageName("a"), PackageName("b"), setOf(ClassName("a.X") to ClassName("b.Y"))),
            ),
        )

        val ranked = CycleBreakAnalyzer.rankEdges(cycle)

        assertEquals(1, ranked[0].weight, "Lightest edge first")
        assertEquals(3, ranked[1].weight, "Heaviest edge last")
    }
}
