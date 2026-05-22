package no.f12.codenavigator.navigation.dsm

import no.f12.codenavigator.navigation.types.ClassName
import no.f12.codenavigator.navigation.types.PackageName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CohesionScorerTest {

    // [TEST] Empty dependencies produces empty cohesion result
    @Test
    fun `empty dependencies produces empty cohesion result`() {
        val result = CohesionScorer.score(emptyList())

        assertTrue(result.entries.isEmpty())
    }

    // [TEST] Single package with only internal deps has cohesion 1.0
    @Test
    fun `single package with only internal deps has cohesion 1_0`() {
        val deps = listOf(
            PackageDependency(PackageName("com.example"), PackageName("com.example"), ClassName("com.example.A"), ClassName("com.example.B")),
            PackageDependency(PackageName("com.example"), PackageName("com.example"), ClassName("com.example.B"), ClassName("com.example.A")),
        )

        val result = CohesionScorer.score(deps)

        assertEquals(1, result.entries.size)
        assertEquals(PackageName("com.example"), result.entries[0].packageName)
        assertEquals(2, result.entries[0].internalEdges)
        assertEquals(0, result.entries[0].externalEdges)
        assertEquals(1.0, result.entries[0].cohesion)
    }

    @Test
    fun `single package with only external deps has cohesion 0_0`() {
        val deps = listOf(
            PackageDependency(PackageName("com.example"), PackageName("com.other"), ClassName("com.example.A"), ClassName("com.other.X")),
        )

        val result = CohesionScorer.score(deps)

        val entry = result.entries.first { it.packageName == PackageName("com.example") }
        assertEquals(0, entry.internalEdges)
        assertEquals(1, entry.externalEdges)
        assertEquals(0.0, entry.cohesion)
    }

    @Test
    fun `mixed internal and external deps produces correct ratio`() {
        val deps = listOf(
            PackageDependency(PackageName("com.example"), PackageName("com.example"), ClassName("com.example.A"), ClassName("com.example.B")),
            PackageDependency(PackageName("com.example"), PackageName("com.other"), ClassName("com.example.A"), ClassName("com.other.X")),
            PackageDependency(PackageName("com.example"), PackageName("com.other"), ClassName("com.example.B"), ClassName("com.other.Y")),
        )

        val result = CohesionScorer.score(deps)

        val entry = result.entries.first { it.packageName == PackageName("com.example") }
        assertEquals(1, entry.internalEdges)
        assertEquals(2, entry.externalEdges)
        assertEquals(1.0 / 3.0, entry.cohesion, 0.001)
    }

    @Test
    fun `multiple packages are scored independently`() {
        val deps = listOf(
            PackageDependency(PackageName("com.a"), PackageName("com.a"), ClassName("com.a.X"), ClassName("com.a.Y")),
            PackageDependency(PackageName("com.b"), PackageName("com.a"), ClassName("com.b.Z"), ClassName("com.a.X")),
        )

        val result = CohesionScorer.score(deps)

        val entryA = result.entries.first { it.packageName == PackageName("com.a") }
        val entryB = result.entries.first { it.packageName == PackageName("com.b") }
        assertEquals(1.0, entryA.cohesion)
        assertEquals(0.0, entryB.cohesion)
    }

    @Test
    fun `packages are sorted by cohesion ascending`() {
        val deps = listOf(
            PackageDependency(PackageName("com.good"), PackageName("com.good"), ClassName("com.good.A"), ClassName("com.good.B")),
            PackageDependency(PackageName("com.bad"), PackageName("com.other"), ClassName("com.bad.X"), ClassName("com.other.Y")),
        )

        val result = CohesionScorer.score(deps)

        assertEquals(PackageName("com.bad"), result.entries[0].packageName)
        assertEquals(PackageName("com.good"), result.entries[1].packageName)
    }

    @Test
    fun `top parameter limits output count`() {
        val deps = listOf(
            PackageDependency(PackageName("com.a"), PackageName("com.a"), ClassName("com.a.X"), ClassName("com.a.Y")),
            PackageDependency(PackageName("com.b"), PackageName("com.c"), ClassName("com.b.X"), ClassName("com.c.Y")),
            PackageDependency(PackageName("com.c"), PackageName("com.a"), ClassName("com.c.X"), ClassName("com.a.Y")),
        )

        val result = CohesionScorer.score(deps, top = 2)

        assertEquals(2, result.entries.size)
    }
}
