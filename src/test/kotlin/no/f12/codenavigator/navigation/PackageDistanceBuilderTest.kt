package no.f12.codenavigator.navigation

import no.f12.codenavigator.navigation.dsm.DsmMatrix
import no.f12.codenavigator.navigation.dsm.DsmMatrixBuilder
import no.f12.codenavigator.navigation.dsm.PackageDependency
import no.f12.codenavigator.navigation.dsm.PackageDistanceBuilder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PackageDistanceBuilderTest {

    @Test
    fun `empty matrix produces empty result`() {
        val matrix = DsmMatrix(emptyList(), emptyMap(), emptyMap())

        val result = PackageDistanceBuilder.build(matrix)

        assertTrue(result.entries.isEmpty())
    }
    @Test
    fun `single dependency produces one distance entry`() {
        val matrix = buildMatrix(
            PackageDependency(PackageName("com.example.api"), PackageName("com.example.model"), ClassName("Ctrl"), ClassName("User")),
        )

        val result = PackageDistanceBuilder.build(matrix)

        assertEquals(1, result.entries.size)
        val entry = result.entries.first()
        assertEquals(PackageName("api"), entry.source)
        assertEquals(PackageName("model"), entry.target)
        assertEquals(2, entry.distance)
        assertEquals(1, entry.dependencyCount)
    }

    private fun buildMatrix(vararg deps: PackageDependency): DsmMatrix =
        DsmMatrixBuilder.build(deps.toList(), PackageName("com.example"), 1)
    @Test
    fun `bidirectional dependency produces two distance entries`() {
        val matrix = buildMatrix(
            PackageDependency(PackageName("com.example.api"), PackageName("com.example.model"), ClassName("Ctrl"), ClassName("User")),
            PackageDependency(PackageName("com.example.model"), PackageName("com.example.api"), ClassName("User"), ClassName("Ctrl")),
        )

        val result = PackageDistanceBuilder.build(matrix)

        assertEquals(2, result.entries.size)
        val sources = result.entries.map { it.source }.toSet()
        assertEquals(setOf(PackageName("api"), PackageName("model")), sources)
    }

    @Test
    fun `results are sorted by descending distance`() {
        val matrix = DsmMatrixBuilder.build(
            listOf(
                PackageDependency(PackageName("com.example.api"), PackageName("com.example.model"), ClassName("Ctrl"), ClassName("User")),
                PackageDependency(PackageName("com.example.api.rest.v1"), PackageName("com.example.infra.db.impl"), ClassName("Handler"), ClassName("Repo")),
            ),
            PackageName("com.example"),
            3,
        )

        val result = PackageDistanceBuilder.build(matrix)

        assertTrue(result.entries.size >= 2)
        val distances = result.entries.map { it.distance }
        assertEquals(distances.sortedDescending(), distances)
    }

    @Test
    fun `top parameter limits results`() {
        val matrix = buildMatrix(
            PackageDependency(PackageName("com.example.api"), PackageName("com.example.model"), ClassName("Ctrl"), ClassName("User")),
            PackageDependency(PackageName("com.example.service"), PackageName("com.example.repo"), ClassName("Svc"), ClassName("Repo")),
            PackageDependency(PackageName("com.example.api"), PackageName("com.example.service"), ClassName("Ctrl"), ClassName("Svc")),
        )

        val result = PackageDistanceBuilder.build(matrix, top = 2)

        assertEquals(2, result.entries.size)
    }

    @Test
    fun `all pre-filtered entries are included without additional filtering`() {
        val matrix = buildMatrix(
            PackageDependency(PackageName("com.example.api"), PackageName("com.example.model"), ClassName("Ctrl"), ClassName("User")),
            PackageDependency(PackageName("com.example.service"), PackageName("com.example.repo"), ClassName("Svc"), ClassName("Repo")),
        )

        val result = PackageDistanceBuilder.build(matrix)

        assertEquals(2, result.entries.size)
    }

    @Test
    fun `pre-filtered matrix with shortened display names returns all entries`() {
        val matrix = DsmMatrixBuilder.build(
            listOf(
                PackageDependency(PackageName("com.example.api"), PackageName("com.example.api.routes"), ClassName("Ctrl"), ClassName("Routes")),
                PackageDependency(PackageName("com.example.api.routes"), PackageName("com.example.api"), ClassName("Routes"), ClassName("Ctrl")),
            ),
            PackageName("com.example"),
            2,
        )

        val result = PackageDistanceBuilder.build(matrix)

        assertEquals(2, result.entries.size)
    }

    @Test
    fun `dependency count is included in result`() {
        val matrix = buildMatrix(
            PackageDependency(PackageName("com.example.api"), PackageName("com.example.model"), ClassName("Ctrl"), ClassName("User")),
            PackageDependency(PackageName("com.example.api"), PackageName("com.example.model"), ClassName("Ctrl"), ClassName("Order")),
            PackageDependency(PackageName("com.example.api"), PackageName("com.example.model"), ClassName("Handler"), ClassName("Item")),
        )

        val result = PackageDistanceBuilder.build(matrix)

        assertEquals(1, result.entries.size)
        assertEquals(3, result.entries.first().dependencyCount)
    }
}
