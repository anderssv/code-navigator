package no.f12.codenavigator.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CycleDetectorTest {

    @Test
    fun `empty graph has no cycles`() {
        val graph = emptyMap<PackageName, Set<PackageName>>()

        val cycles = CycleDetector.findCycles(graph)

        assertTrue(cycles.isEmpty())
    }

    @Test
    fun `single node with no edges has no cycles`() {
        val graph = mapOf(PackageName("api") to emptySet<PackageName>())

        val cycles = CycleDetector.findCycles(graph)

        assertTrue(cycles.isEmpty())
    }

    @Test
    fun `two nodes with one-directional edge — no cycle`() {
        val graph = mapOf(PackageName("api") to setOf(PackageName("service")))

        val cycles = CycleDetector.findCycles(graph)

        assertTrue(cycles.isEmpty())
    }

    @Test
    fun `two nodes with bidirectional edges — one cycle of size 2`() {
        val graph = mapOf(
            PackageName("api") to setOf(PackageName("service")),
            PackageName("service") to setOf(PackageName("api")),
        )

        val cycles = CycleDetector.findCycles(graph)

        assertEquals(1, cycles.size)
        assertEquals(2, cycles.first().packages.size)
        assertTrue(cycles.first().packages.containsAll(listOf(PackageName("api"), PackageName("service"))))
    }

    @Test
    fun `three nodes in a triangle — one cycle of size 3`() {
        val graph = mapOf(
            PackageName("api") to setOf(PackageName("service")),
            PackageName("service") to setOf(PackageName("repo")),
            PackageName("repo") to setOf(PackageName("api")),
        )

        val cycles = CycleDetector.findCycles(graph)

        assertEquals(1, cycles.size)
        assertEquals(3, cycles.first().packages.size)
        assertTrue(cycles.first().packages.containsAll(listOf(PackageName("api"), PackageName("service"), PackageName("repo"))))
    }

    @Test
    fun `two separate cycles are both detected`() {
        val graph = mapOf(
            PackageName("api") to setOf(PackageName("service")),
            PackageName("service") to setOf(PackageName("api")),
            PackageName("domain") to setOf(PackageName("infra")),
            PackageName("infra") to setOf(PackageName("domain")),
        )

        val cycles = CycleDetector.findCycles(graph)

        assertEquals(2, cycles.size)
    }

    @Test
    fun `tail leading into a cycle — only the cycle is reported`() {
        val graph = mapOf(
            PackageName("entry") to setOf(PackageName("api")),
            PackageName("api") to setOf(PackageName("service")),
            PackageName("service") to setOf(PackageName("api")),
        )

        val cycles = CycleDetector.findCycles(graph)

        assertEquals(1, cycles.size)
        assertEquals(2, cycles.first().packages.size)
        assertTrue(cycles.first().packages.containsAll(listOf(PackageName("api"), PackageName("service"))))
    }

    @Test
    fun `self-loop is not reported`() {
        val graph = mapOf(PackageName("api") to setOf(PackageName("api")))

        val cycles = CycleDetector.findCycles(graph)

        assertTrue(cycles.isEmpty())
    }

    @Test
    fun `large SCC with multiple internal paths reports as one cycle`() {
        val graph = mapOf(
            PackageName("a") to setOf(PackageName("b")),
            PackageName("b") to setOf(PackageName("c"), PackageName("a")),
            PackageName("c") to setOf(PackageName("a")),
        )

        val cycles = CycleDetector.findCycles(graph)

        assertEquals(1, cycles.size)
        assertEquals(3, cycles.first().packages.size)
    }

    @Test
    fun `cycles are sorted by size ascending`() {
        val graph = mapOf(
            PackageName("a") to setOf(PackageName("b")),
            PackageName("b") to setOf(PackageName("c")),
            PackageName("c") to setOf(PackageName("a")),
            PackageName("x") to setOf(PackageName("y")),
            PackageName("y") to setOf(PackageName("x")),
        )

        val cycles = CycleDetector.findCycles(graph)

        assertEquals(2, cycles.size)
        assertEquals(2, cycles[0].packages.size, "Smaller cycle first")
        assertEquals(3, cycles[1].packages.size, "Larger cycle second")
    }

    @Test
    fun `adjacencyMapFrom extracts directed edges from DsmMatrix`() {
        val matrix = DsmMatrix(
            packages = listOf(PackageName("api"), PackageName("service")),
            cells = mapOf(
                (PackageName("api") to PackageName("service")) to 3,
                (PackageName("service") to PackageName("api")) to 1,
            ),
            classDependencies = emptyMap(),
        )

        val adjacency = CycleDetector.adjacencyMapFrom(matrix)

        assertEquals(setOf(PackageName("service")), adjacency[PackageName("api")])
        assertEquals(setOf(PackageName("api")), adjacency[PackageName("service")])
    }

    @Test
    fun `enrich adds class-level edges from DsmMatrix to each cycle`() {
        val matrix = DsmMatrix(
            packages = listOf(PackageName("api"), PackageName("service")),
            cells = mapOf(
                (PackageName("api") to PackageName("service")) to 2,
                (PackageName("service") to PackageName("api")) to 1,
            ),
            classDependencies = mapOf(
                (PackageName("api") to PackageName("service")) to setOf(ClassName("api.Controller") to ClassName("service.Service"), ClassName("api.Filter") to ClassName("service.Service")),
                (PackageName("service") to PackageName("api")) to setOf(ClassName("service.Service") to ClassName("api.Controller")),
            ),
        )

        val cycles = listOf(Cycle(packages = listOf(PackageName("api"), PackageName("service"))))
        val details = CycleDetector.enrich(cycles, matrix)

        assertEquals(1, details.size)
        val detail = details.first()
        assertEquals(listOf(PackageName("api"), PackageName("service")), detail.packages)
        assertEquals(2, detail.edges.size, "Two directions of edges")

        val apiToService = detail.edges.find { it.from == PackageName("api") && it.to == PackageName("service") }!!
        assertEquals(2, apiToService.classEdges.size)

        val serviceToApi = detail.edges.find { it.from == PackageName("service") && it.to == PackageName("api") }!!
        assertEquals(1, serviceToApi.classEdges.size)
    }

    @Test
    fun `hub-and-spoke bidirectional edges — finds one large SCC`() {
        val graph = mapOf(
            PackageName("admin") to setOf(PackageName("no.mikill")),
            PackageName("creation") to setOf(PackageName("no.mikill")),
            PackageName("mypolls") to setOf(PackageName("no.mikill")),
            PackageName("participant") to setOf(PackageName("no.mikill")),
            PackageName("transfer") to setOf(PackageName("no.mikill")),
            PackageName("no.mikill") to setOf(
                PackageName("admin"),
                PackageName("creation"),
                PackageName("mypolls"),
                PackageName("participant"),
                PackageName("transfer"),
            ),
        )

        val cycles = CycleDetector.findCycles(graph)

        assertEquals(1, cycles.size, "All connected through hub: one large SCC")
        assertEquals(6, cycles.first().packages.size)
    }

    @Test
    fun `findCycles and findCyclicPairs both detect cycles from same matrix`() {
        val deps = listOf(
            PackageDependency(PackageName("com.example.admin"), PackageName("com.example.root"), ClassName("com.example.admin.Routes"), ClassName("com.example.root.App")),
            PackageDependency(PackageName("com.example.root"), PackageName("com.example.admin"), ClassName("com.example.root.App"), ClassName("com.example.admin.Routes")),
        )
        val matrix = DsmMatrixBuilder.build(deps, PackageName("com.example"), 2)

        val cyclicPairs = matrix.findCyclicPairs()
        val adjacency = CycleDetector.adjacencyMapFrom(matrix)
        val cycles = CycleDetector.findCycles(adjacency)

        assertTrue(cyclicPairs.isNotEmpty(), "findCyclicPairs should find the bidirectional dependency")
        assertTrue(cycles.isNotEmpty(), "findCycles should also find the cycle")
    }

    @Test
    fun `root-package classes truncated to wrong name still detected as cycles`() {
        val rootPkg = PackageName("no.mikill.greitt")
        val deps = listOf(
            PackageDependency(PackageName("no.mikill.greitt.admin"), PackageName("no.mikill.greitt"), ClassName("no.mikill.greitt.admin.AdminRoutesKt"), ClassName("no.mikill.greitt.AppDependencies")),
            PackageDependency(PackageName("no.mikill.greitt"), PackageName("no.mikill.greitt.admin"), ClassName("no.mikill.greitt.RoutesKt"), ClassName("no.mikill.greitt.admin.AdminRoutesKt")),
        )
        val matrix = DsmMatrixBuilder.build(deps, rootPkg, 2)

        val cyclicPairs = matrix.findCyclicPairs()
        val adjacency = CycleDetector.adjacencyMapFrom(matrix)
        val cycles = CycleDetector.findCycles(adjacency)

        assertTrue(cyclicPairs.isNotEmpty(), "findCyclicPairs should find the bidirectional dependency")
        assertTrue(cycles.isNotEmpty(), "findCycles should also find the cycle")
    }
}
