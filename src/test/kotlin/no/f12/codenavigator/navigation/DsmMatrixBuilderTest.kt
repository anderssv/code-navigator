package no.f12.codenavigator.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DsmMatrixBuilderTest {

    @Test
    fun `empty dependency list produces empty matrix`() {
        val matrix = DsmMatrixBuilder.build(emptyList(), "", 2)

        assertTrue(matrix.packages.isEmpty())
        assertTrue(matrix.cells.isEmpty())
    }

    @Test
    fun `single dependency produces matrix with two packages`() {
        val deps = listOf(
            PackageDependency("com.example.api", "com.example.model", "UserController", "User"),
        )

        val matrix = DsmMatrixBuilder.build(deps, "com.example", 1)

        assertEquals(listOf("api", "model"), matrix.packages)
        assertEquals(1, matrix.cells["api" to "model"])
    }

    @Test
    fun `dependencies are aggregated by count`() {
        val deps = listOf(
            PackageDependency("com.example.api", "com.example.model", "UserController", "User"),
            PackageDependency("com.example.api", "com.example.model", "UserController", "Order"),
            PackageDependency("com.example.api", "com.example.model", "OrderController", "Order"),
        )

        val matrix = DsmMatrixBuilder.build(deps, "com.example", 1)

        assertEquals(3, matrix.cells["api" to "model"])
    }

    @Test
    fun `self-package dependencies after truncation are excluded`() {
        val deps = listOf(
            PackageDependency("com.example.api.v1", "com.example.api.v2", "FooController", "BarController"),
        )

        val matrix = DsmMatrixBuilder.build(deps, "com.example", 1)

        assertTrue(matrix.packages.isEmpty())
        assertTrue(matrix.cells.isEmpty())
    }

    @Test
    fun `root prefix is stripped from package names`() {
        val deps = listOf(
            PackageDependency("com.example.service", "com.example.repository", "UserService", "UserRepo"),
        )

        val matrix = DsmMatrixBuilder.build(deps, "com.example", 1)

        assertEquals(listOf("repository", "service"), matrix.packages)
        assertEquals(1, matrix.cells["service" to "repository"])
    }

    @Test
    fun `depth truncates package segments`() {
        val deps = listOf(
            PackageDependency("com.example.api.rest.v1", "com.example.service.impl", "Controller", "ServiceImpl"),
        )

        val matrix = DsmMatrixBuilder.build(deps, "com.example", 2)

        assertEquals(listOf("api.rest", "service.impl"), matrix.packages)
    }

    @Test
    fun `depth of 1 groups to top level`() {
        val deps = listOf(
            PackageDependency("com.example.api.rest.v1", "com.example.service.impl", "Controller", "ServiceImpl"),
        )

        val matrix = DsmMatrixBuilder.build(deps, "com.example", 1)

        assertEquals(listOf("api", "service"), matrix.packages)
    }

    @Test
    fun `class-level dependency details are tracked`() {
        val deps = listOf(
            PackageDependency("com.example.api", "com.example.model", "UserController", "User"),
            PackageDependency("com.example.api", "com.example.model", "OrderController", "Order"),
        )

        val matrix = DsmMatrixBuilder.build(deps, "com.example", 1)

        val classDeps = matrix.classDependencies["api" to "model"]
        assertEquals(setOf("UserController" to "User", "OrderController" to "Order"), classDeps)
    }

    @Test
    fun `packages are sorted alphabetically`() {
        val deps = listOf(
            PackageDependency("com.example.service", "com.example.api", "Svc", "Ctrl"),
            PackageDependency("com.example.api", "com.example.model", "Ctrl", "User"),
        )

        val matrix = DsmMatrixBuilder.build(deps, "com.example", 1)

        assertEquals(listOf("api", "model", "service"), matrix.packages)
    }

    @Test
    fun `no root prefix with insufficient depth collapses to self-dependency`() {
        val deps = listOf(
            PackageDependency("com.example.api", "com.example.model", "Ctrl", "User"),
        )

        val matrix = DsmMatrixBuilder.build(deps, "", 2)

        assertTrue(matrix.packages.isEmpty())
        assertTrue(matrix.cells.isEmpty())
    }

    @Test
    fun `no root prefix with sufficient depth shows package segments`() {
        val deps = listOf(
            PackageDependency("com.example.api", "com.example.model", "Ctrl", "User"),
        )

        val matrix = DsmMatrixBuilder.build(deps, "", 3)

        assertEquals(listOf("com.example.api", "com.example.model"), matrix.packages)
    }
}
