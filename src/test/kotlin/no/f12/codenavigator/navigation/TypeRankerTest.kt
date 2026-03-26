package no.f12.codenavigator.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TypeRankerTest {

    @Test
    fun `empty call graph produces empty ranking`() {
        val graph = testCallGraph()

        val ranked = TypeRanker.rank(graph)

        assertTrue(ranked.isEmpty())
    }
    @Test
    fun `single edge produces two ranked types`() {
        val graph = testCallGraph(
            method("com.example.Caller", "doWork") to method("com.example.Target", "process"),
        )

        val ranked = TypeRanker.rank(graph)

        assertEquals(2, ranked.size)
        val target = ranked.find { it.className.value == "com.example.Target" }!!
        assertEquals(1, target.inDegree)
        assertEquals(0, target.outDegree)
        val caller = ranked.find { it.className.value == "com.example.Caller" }!!
        assertEquals(0, caller.inDegree)
        assertEquals(1, caller.outDegree)
    }
    @Test
    fun `type called by many others ranks higher than isolated type`() {
        val graph = testCallGraph(
            method("com.example.A", "doA") to method("com.example.Core", "process"),
            method("com.example.B", "doB") to method("com.example.Core", "process"),
            method("com.example.C", "doC") to method("com.example.Core", "process"),
            method("com.example.A", "doA") to method("com.example.Leaf", "leaf"),
        )

        val ranked = TypeRanker.rank(graph)

        val coreRank = ranked.find { it.className.value == "com.example.Core" }!!.rank
        val leafRank = ranked.find { it.className.value == "com.example.Leaf" }!!.rank
        assertTrue(coreRank > leafRank, "Core (called by 3) should rank higher than Leaf (called by 1)")
    }
    @Test
    fun `transitive importance — type called by high-rank type ranks higher`() {
        val graph = testCallGraph(
            method("com.example.A", "a") to method("com.example.Hub", "hub"),
            method("com.example.B", "b") to method("com.example.Hub", "hub"),
            method("com.example.C", "c") to method("com.example.Hub", "hub"),
            method("com.example.Hub", "hub") to method("com.example.Important", "work"),
            method("com.example.Leaf", "leaf") to method("com.example.Minor", "work"),
        )

        val ranked = TypeRanker.rank(graph)

        val importantRank = ranked.find { it.className.value == "com.example.Important" }!!.rank
        val minorRank = ranked.find { it.className.value == "com.example.Minor" }!!.rank
        assertTrue(importantRank > minorRank, "Important (called by Hub which is called by 3) should rank higher than Minor (called by Leaf only)")
    }
    @Test
    fun `results are sorted by rank descending`() {
        val graph = testCallGraph(
            method("com.example.A", "a") to method("com.example.Core", "c"),
            method("com.example.B", "b") to method("com.example.Core", "c"),
            method("com.example.C", "c") to method("com.example.Core", "c"),
            method("com.example.A", "a") to method("com.example.Mid", "m"),
        )

        val ranked = TypeRanker.rank(graph)

        for (i in 0 until ranked.size - 1) {
            assertTrue(ranked[i].rank >= ranked[i + 1].rank, "Results should be sorted by rank descending")
        }
    }
    @Test
    fun `returns correct inDegree and outDegree counts`() {
        val graph = testCallGraph(
            method("com.example.A", "a1") to method("com.example.B", "b1"),
            method("com.example.A", "a2") to method("com.example.C", "c1"),
            method("com.example.B", "b1") to method("com.example.C", "c1"),
        )

        val ranked = TypeRanker.rank(graph)

        val a = ranked.find { it.className.value == "com.example.A" }!!
        assertEquals(0, a.inDegree)
        assertEquals(2, a.outDegree)
        val b = ranked.find { it.className.value == "com.example.B" }!!
        assertEquals(1, b.inDegree)
        assertEquals(1, b.outDegree)
        val c = ranked.find { it.className.value == "com.example.C" }!!
        assertEquals(2, c.inDegree)
        assertEquals(0, c.outDegree)
    }
    @Test
    fun `top parameter limits number of results`() {
        val graph = testCallGraph(
            method("com.example.A", "a") to method("com.example.B", "b"),
            method("com.example.B", "b") to method("com.example.C", "c"),
            method("com.example.C", "c") to method("com.example.D", "d"),
        )

        val ranked = TypeRanker.rank(graph, top = 2)

        assertEquals(2, ranked.size)
    }
    @Test
    fun `projectOnly filter excludes external classes`() {
        val graph = testCallGraph(
            method("com.example.Service", "work") to method("java.util.List", "add"),
            method("com.example.Controller", "handle") to method("com.example.Service", "work"),
            projectClasses = setOf("com.example.Service", "com.example.Controller"),
        )

        val ranked = TypeRanker.rank(graph, projectOnly = true)

        val classNames = ranked.map { it.className.value }.toSet()
        assertTrue("com.example.Service" in classNames)
        assertTrue("com.example.Controller" in classNames)
        assertTrue("java.util.List" !in classNames, "External class should be excluded")
    }

    @Test
    fun `lambda classes collapse into enclosing class`() {
        val graph = testCallGraph(
            method("com.example.Controller", "handle") to method("com.example.Service", "work"),
            method("com.example.Controller\$handle\$1", "invoke") to method("com.example.Service", "work"),
            method("com.example.Controller\$handle\$2", "invoke") to method("com.example.Service", "work"),
        )

        val ranked = TypeRanker.rank(graph, collapseLambdas = true)

        val classNames = ranked.map { it.className.value }.toSet()
        assertTrue("com.example.Controller\$handle\$1" !in classNames, "Lambda classes should not appear")
        assertTrue("com.example.Controller\$handle\$2" !in classNames, "Lambda classes should not appear")
        assertTrue("com.example.Controller" in classNames)
        assertTrue("com.example.Service" in classNames)
        assertEquals(2, ranked.size, "Should have only Controller and Service after collapsing")
    }

}
