package no.f12.codenavigator.navigation.dsm

import no.f12.codenavigator.navigation.types.ClassName
import no.f12.codenavigator.navigation.types.PackageName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SimulateMoveAnalyzerTest {

    // [TEST] Moving a class that causes a cycle breaks the cycle
    // [TEST] Moving a class that doesn't affect cycles reports no change
    // [TEST] Moving a class that creates a new cycle reports it as added
    // [TEST] Resolves simple class name to fully qualified name
    // [TEST] Reports error when class not found

    @Test
    fun `moving class that causes a cycle breaks the cycle`() {
        val dependencies = listOf(
            PackageDependency(PackageName("api"), PackageName("service"), ClassName("api.Controller"), ClassName("service.Service")),
            PackageDependency(PackageName("service"), PackageName("api"), ClassName("service.Service"), ClassName("api.Dto")),
        )

        val result = SimulateMoveAnalyzer.analyze(
            dependencies = dependencies,
            classToMove = ClassName("api.Dto"),
            targetPackage = PackageName("service"),
        )

        assertEquals(1, result.cyclesBefore)
        assertEquals(0, result.cyclesAfter)
        assertEquals(1, result.removedCycles.size)
        assertTrue(result.addedCycles.isEmpty())
    }

    @Test
    fun `moving class that does not affect cycles reports no change`() {
        val dependencies = listOf(
            PackageDependency(PackageName("api"), PackageName("service"), ClassName("api.Controller"), ClassName("service.Service")),
            PackageDependency(PackageName("service"), PackageName("api"), ClassName("service.Service"), ClassName("api.Dto")),
            PackageDependency(PackageName("util"), PackageName("api"), ClassName("util.Helper"), ClassName("api.Controller")),
        )

        val result = SimulateMoveAnalyzer.analyze(
            dependencies = dependencies,
            classToMove = ClassName("util.Helper"),
            targetPackage = PackageName("common"),
        )

        assertEquals(1, result.cyclesBefore)
        assertEquals(1, result.cyclesAfter)
        assertTrue(result.removedCycles.isEmpty())
        assertTrue(result.addedCycles.isEmpty())
    }

    @Test
    fun `moving class that creates a new cycle reports it as added`() {
        // Initially no cycle: api -> service, service -> util
        // Moving util.Helper to api creates: api -> service -> api (via Helper)
        val dependencies = listOf(
            PackageDependency(PackageName("api"), PackageName("service"), ClassName("api.Controller"), ClassName("service.Service")),
            PackageDependency(PackageName("service"), PackageName("util"), ClassName("service.Service"), ClassName("util.Helper")),
        )

        val result = SimulateMoveAnalyzer.analyze(
            dependencies = dependencies,
            classToMove = ClassName("util.Helper"),
            targetPackage = PackageName("api"),
        )

        assertEquals(0, result.cyclesBefore)
        assertEquals(1, result.cyclesAfter)
        assertTrue(result.removedCycles.isEmpty())
        assertEquals(1, result.addedCycles.size)
    }
}
