package no.f12.codenavigator.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CycleDetectorTest {

    @Test
    fun `empty graph has no cycles`() {
        val graph = emptyMap<String, Set<String>>()

        val cycles = CycleDetector.findCycles(graph)

        assertTrue(cycles.isEmpty())
    }

    @Test
    fun `single node with no edges has no cycles`() {
        val graph = mapOf("api" to emptySet<String>())

        val cycles = CycleDetector.findCycles(graph)

        assertTrue(cycles.isEmpty())
    }

    @Test
    fun `two nodes with one-directional edge — no cycle`() {
        val graph = mapOf("api" to setOf("service"))

        val cycles = CycleDetector.findCycles(graph)

        assertTrue(cycles.isEmpty())
    }

    @Test
    fun `two nodes with bidirectional edges — one cycle of size 2`() {
        val graph = mapOf(
            "api" to setOf("service"),
            "service" to setOf("api"),
        )

        val cycles = CycleDetector.findCycles(graph)

        assertEquals(1, cycles.size)
        assertEquals(2, cycles.first().packages.size)
        assertTrue(cycles.first().packages.containsAll(listOf("api", "service")))
    }

    @Test
    fun `three nodes in a triangle — one cycle of size 3`() {
        val graph = mapOf(
            "api" to setOf("service"),
            "service" to setOf("repo"),
            "repo" to setOf("api"),
        )

        val cycles = CycleDetector.findCycles(graph)

        assertEquals(1, cycles.size)
        assertEquals(3, cycles.first().packages.size)
        assertTrue(cycles.first().packages.containsAll(listOf("api", "service", "repo")))
    }

    @Test
    fun `two separate cycles are both detected`() {
        val graph = mapOf(
            "api" to setOf("service"),
            "service" to setOf("api"),
            "domain" to setOf("infra"),
            "infra" to setOf("domain"),
        )

        val cycles = CycleDetector.findCycles(graph)

        assertEquals(2, cycles.size)
    }

    @Test
    fun `tail leading into a cycle — only the cycle is reported`() {
        val graph = mapOf(
            "entry" to setOf("api"),
            "api" to setOf("service"),
            "service" to setOf("api"),
        )

        val cycles = CycleDetector.findCycles(graph)

        assertEquals(1, cycles.size)
        assertEquals(2, cycles.first().packages.size)
        assertTrue(cycles.first().packages.containsAll(listOf("api", "service")))
    }

    @Test
    fun `self-loop is not reported`() {
        val graph = mapOf("api" to setOf("api"))

        val cycles = CycleDetector.findCycles(graph)

        assertTrue(cycles.isEmpty())
    }

    @Test
    fun `large SCC with multiple internal paths reports as one cycle`() {
        val graph = mapOf(
            "a" to setOf("b"),
            "b" to setOf("c", "a"),
            "c" to setOf("a"),
        )

        val cycles = CycleDetector.findCycles(graph)

        assertEquals(1, cycles.size)
        assertEquals(3, cycles.first().packages.size)
    }

    @Test
    fun `cycles are sorted by size ascending`() {
        val graph = mapOf(
            "a" to setOf("b"),
            "b" to setOf("c"),
            "c" to setOf("a"),
            "x" to setOf("y"),
            "y" to setOf("x"),
        )

        val cycles = CycleDetector.findCycles(graph)

        assertEquals(2, cycles.size)
        assertEquals(2, cycles[0].packages.size, "Smaller cycle first")
        assertEquals(3, cycles[1].packages.size, "Larger cycle second")
    }

    @Test
    fun `adjacencyMapFrom extracts directed edges from DsmMatrix`() {
        val matrix = DsmMatrix(
            packages = listOf("api", "service"),
            cells = mapOf(
                ("api" to "service") to 3,
                ("service" to "api") to 1,
            ),
            classDependencies = emptyMap(),
        )

        val adjacency = CycleDetector.adjacencyMapFrom(matrix)

        assertEquals(setOf("service"), adjacency["api"])
        assertEquals(setOf("api"), adjacency["service"])
    }

    @Test
    fun `enrich adds class-level edges from DsmMatrix to each cycle`() {
        val matrix = DsmMatrix(
            packages = listOf("api", "service"),
            cells = mapOf(
                ("api" to "service") to 2,
                ("service" to "api") to 1,
            ),
            classDependencies = mapOf(
                ("api" to "service") to setOf("api.Controller" to "service.Service", "api.Filter" to "service.Service"),
                ("service" to "api") to setOf("service.Service" to "api.Controller"),
            ),
        )

        val cycles = listOf(Cycle(packages = listOf("api", "service")))
        val details = CycleDetector.enrich(cycles, matrix)

        assertEquals(1, details.size)
        val detail = details.first()
        assertEquals(listOf("api", "service"), detail.packages)
        assertEquals(2, detail.edges.size, "Two directions of edges")

        val apiToService = detail.edges.find { it.from == "api" && it.to == "service" }!!
        assertEquals(2, apiToService.classEdges.size)

        val serviceToApi = detail.edges.find { it.from == "service" && it.to == "api" }!!
        assertEquals(1, serviceToApi.classEdges.size)
    }
}
