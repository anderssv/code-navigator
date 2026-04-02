package no.f12.codenavigator.navigation

import no.f12.codenavigator.navigation.dsm.DsmMatrixBuilder
import no.f12.codenavigator.navigation.dsm.PackageDependency
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DsmMatrixBuilderTest {

    @Test
    fun `empty dependency list produces empty matrix`() {
        val matrix = DsmMatrixBuilder.build(emptyList(), PackageName(""), 2)

        assertTrue(matrix.packages.isEmpty())
        assertTrue(matrix.cells.isEmpty())
    }

    @Test
    fun `single dependency produces matrix with two packages`() {
        val deps = listOf(
            PackageDependency(PackageName("com.example.api"), PackageName("com.example.model"), ClassName("UserController"), ClassName("User")),
        )

        val matrix = DsmMatrixBuilder.build(deps, PackageName("com.example"), 1)

        assertEquals(listOf(PackageName("api"), PackageName("model")), matrix.packages)
        assertEquals(1, matrix.cells[PackageName("api") to PackageName("model")])
    }

    @Test
    fun `dependencies are aggregated by count`() {
        val deps = listOf(
            PackageDependency(PackageName("com.example.api"), PackageName("com.example.model"), ClassName("UserController"), ClassName("User")),
            PackageDependency(PackageName("com.example.api"), PackageName("com.example.model"), ClassName("UserController"), ClassName("Order")),
            PackageDependency(PackageName("com.example.api"), PackageName("com.example.model"), ClassName("OrderController"), ClassName("Order")),
        )

        val matrix = DsmMatrixBuilder.build(deps, PackageName("com.example"), 1)

        assertEquals(3, matrix.cells[PackageName("api") to PackageName("model")])
    }

    @Test
    fun `self-package dependencies after truncation are excluded`() {
        val deps = listOf(
            PackageDependency(PackageName("com.example.api.v1"), PackageName("com.example.api.v2"), ClassName("FooController"), ClassName("BarController")),
        )

        val matrix = DsmMatrixBuilder.build(deps, PackageName("com.example"), 1)

        assertTrue(matrix.packages.isEmpty())
        assertTrue(matrix.cells.isEmpty())
    }

    @Test
    fun `root prefix is stripped from package names`() {
        val deps = listOf(
            PackageDependency(PackageName("com.example.service"), PackageName("com.example.repository"), ClassName("UserService"), ClassName("UserRepo")),
        )

        val matrix = DsmMatrixBuilder.build(deps, PackageName("com.example"), 1)

        assertEquals(listOf(PackageName("repository"), PackageName("service")), matrix.packages)
        assertEquals(1, matrix.cells[PackageName("service") to PackageName("repository")])
    }

    @Test
    fun `depth truncates package segments`() {
        val deps = listOf(
            PackageDependency(PackageName("com.example.api.rest.v1"), PackageName("com.example.service.impl"), ClassName("Controller"), ClassName("ServiceImpl")),
        )

        val matrix = DsmMatrixBuilder.build(deps, PackageName("com.example"), 2)

        assertEquals(listOf(PackageName("api.rest"), PackageName("service.impl")), matrix.packages)
    }

    @Test
    fun `depth of 1 groups to top level`() {
        val deps = listOf(
            PackageDependency(PackageName("com.example.api.rest.v1"), PackageName("com.example.service.impl"), ClassName("Controller"), ClassName("ServiceImpl")),
        )

        val matrix = DsmMatrixBuilder.build(deps, PackageName("com.example"), 1)

        assertEquals(listOf(PackageName("api"), PackageName("service")), matrix.packages)
    }

    @Test
    fun `class-level dependency details are tracked`() {
        val deps = listOf(
            PackageDependency(PackageName("com.example.api"), PackageName("com.example.model"), ClassName("UserController"), ClassName("User")),
            PackageDependency(PackageName("com.example.api"), PackageName("com.example.model"), ClassName("OrderController"), ClassName("Order")),
        )

        val matrix = DsmMatrixBuilder.build(deps, PackageName("com.example"), 1)

        val classDeps = matrix.classDependencies[PackageName("api") to PackageName("model")]
        assertEquals(setOf(ClassName("UserController") to ClassName("User"), ClassName("OrderController") to ClassName("Order")), classDeps)
    }

    @Test
    fun `packages are sorted alphabetically`() {
        val deps = listOf(
            PackageDependency(PackageName("com.example.service"), PackageName("com.example.api"), ClassName("Svc"), ClassName("Ctrl")),
            PackageDependency(PackageName("com.example.api"), PackageName("com.example.model"), ClassName("Ctrl"), ClassName("User")),
        )

        val matrix = DsmMatrixBuilder.build(deps, PackageName("com.example"), 1)

        assertEquals(listOf(PackageName("api"), PackageName("model"), PackageName("service")), matrix.packages)
    }

    @Test
    fun `no root prefix with insufficient depth collapses to self-dependency`() {
        val deps = listOf(
            PackageDependency(PackageName("com.example.api"), PackageName("com.example.model"), ClassName("Ctrl"), ClassName("User")),
        )

        val matrix = DsmMatrixBuilder.build(deps, PackageName(""), 2)

        assertTrue(matrix.packages.isEmpty())
        assertTrue(matrix.cells.isEmpty())
    }

    @Test
    fun `no root prefix with sufficient depth shows package segments`() {
        val deps = listOf(
            PackageDependency(PackageName("com.example.api"), PackageName("com.example.model"), ClassName("Ctrl"), ClassName("User")),
        )

        val matrix = DsmMatrixBuilder.build(deps, PackageName(""), 3)

        assertEquals(listOf(PackageName("com.example.api"), PackageName("com.example.model")), matrix.packages)
    }

    @Test
    fun `build stores rootPrefix as displayPrefix`() {
        val deps = listOf(
            PackageDependency(PackageName("com.example.api"), PackageName("com.example.model"), ClassName("Ctrl"), ClassName("User")),
        )

        val matrix = DsmMatrixBuilder.build(deps, PackageName("com.example"), 1)

        assertEquals(PackageName("com.example"), matrix.displayPrefix)
    }

    @Test
    fun `build stores empty displayPrefix when rootPrefix is empty`() {
        val deps = listOf(
            PackageDependency(PackageName("com.example.api"), PackageName("com.example.model"), ClassName("Ctrl"), ClassName("User")),
        )

        val matrix = DsmMatrixBuilder.build(deps, PackageName(""), 3)

        assertEquals(PackageName(""), matrix.displayPrefix)
    }

    @Test
    fun `empty dependencies stores empty displayPrefix`() {
        val matrix = DsmMatrixBuilder.build(emptyList(), PackageName("com.example"), 2)

        assertEquals(PackageName(""), matrix.displayPrefix)
    }
}
