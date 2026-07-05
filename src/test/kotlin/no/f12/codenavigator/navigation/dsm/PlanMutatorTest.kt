package no.f12.codenavigator.navigation.dsm

import no.f12.codenavigator.navigation.types.ClassName
import no.f12.codenavigator.navigation.types.PackageName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlanMutatorTest {

    // [TEST] Apply single move rewrites edges involving the moved class
    // [TEST] Apply move filters out self-referencing edges (same package after move)
    // [TEST] Apply multiple moves accumulates mutations
    // [TEST] Empty plan returns dependencies unchanged
    // [TEST] Parse plan file JSON format

    @Test
    fun `apply single move rewrites edges involving the moved class`() {
        val deps = listOf(
            PackageDependency(PackageName("api"), PackageName("service"), ClassName("api.Controller"), ClassName("service.Service")),
            PackageDependency(PackageName("service"), PackageName("api"), ClassName("service.Service"), ClassName("api.Dto")),
        )
        val plan = listOf(PlanStep.Move(ClassName("api.Dto"), PackageName("service")))

        val result = PlanMutator.apply(deps, plan)

        // Dto moved to service: service.Service -> service.Dto is same-package, should be filtered
        assertEquals(1, result.size)
        assertEquals(ClassName("api.Controller"), result[0].sourceClass)
        assertEquals(ClassName("service.Service"), result[0].targetClass)
    }

    @Test
    fun `apply with dropSamePackageEdges=false keeps edges that land in the same package after move`() {
        val deps = listOf(
            PackageDependency(PackageName("api"), PackageName("service"), ClassName("api.Controller"), ClassName("service.Service")),
            PackageDependency(PackageName("service"), PackageName("api"), ClassName("service.Service"), ClassName("api.Dto")),
        )
        val plan = listOf(PlanStep.Move(ClassName("api.Dto"), PackageName("service")))

        val result = PlanMutator.apply(deps, plan, dropSamePackageEdges = false)

        assertEquals(2, result.size)
        val movedEdge = result.first { it.targetClass == ClassName("service.Dto") }
        assertEquals(PackageName("service"), movedEdge.sourcePackage)
        assertEquals(PackageName("service"), movedEdge.targetPackage)
    }

    @Test
    fun `empty plan returns dependencies unchanged`() {
        val deps = listOf(
            PackageDependency(PackageName("api"), PackageName("service"), ClassName("api.Controller"), ClassName("service.Service")),
        )

        val result = PlanMutator.apply(deps, emptyList())

        assertEquals(deps, result)
    }

    @Test
    fun `apply multiple moves accumulates mutations`() {
        val deps = listOf(
            PackageDependency(PackageName("api"), PackageName("service"), ClassName("api.Controller"), ClassName("service.Service")),
            PackageDependency(PackageName("service"), PackageName("api"), ClassName("service.Service"), ClassName("api.Dto")),
            PackageDependency(PackageName("service"), PackageName("util"), ClassName("service.Service"), ClassName("util.Helper")),
        )
        val plan = listOf(
            PlanStep.Move(ClassName("api.Dto"), PackageName("service")),
            PlanStep.Move(ClassName("util.Helper"), PackageName("service")),
        )

        val result = PlanMutator.apply(deps, plan)

        // After both moves: only api.Controller -> service.Service remains cross-package
        assertEquals(1, result.size)
        assertEquals(ClassName("api.Controller"), result[0].sourceClass)
    }

    @Test
    fun `applyToClassSet renames moved classes in project class set`() {
        val projectClasses = setOf(
            ClassName("api.Controller"),
            ClassName("api.Dto"),
            ClassName("service.Service"),
        )
        val plan = listOf(PlanStep.Move(ClassName("api.Dto"), PackageName("service")))

        val result = PlanMutator.applyToClassSet(projectClasses, plan)

        assertEquals(3, result.size)
        assertTrue(ClassName("service.Dto") in result, "Dto should be in service package")
        assertTrue(ClassName("api.Controller") in result)
        assertTrue(ClassName("service.Service") in result)
        assertTrue(ClassName("api.Dto") !in result, "Old FQCN should be gone")
    }

    @Test
    fun `applyToClassSet with empty plan returns unchanged set`() {
        val projectClasses = setOf(ClassName("api.Controller"))

        val result = PlanMutator.applyToClassSet(projectClasses, emptyList())

        assertEquals(projectClasses, result)
    }

    @Test
    fun `parse plan file JSON format`() {
        val json = """[{"action":"move","type":"api.Dto","to":"service"}]"""

        val steps = PlanMutator.parseJson(json)

        assertEquals(1, steps.size)
        val step = steps[0] as PlanStep.Move
        assertEquals(ClassName("api.Dto"), step.classToMove)
        assertEquals(PackageName("service"), step.targetPackage)
    }
}
