package no.f12.codenavigator

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PackageDependencyBuilderTest {

    @Test
    fun `extracts package dependencies from call graph`() {
        val graph = buildCallGraph(
            MethodRef("com.example.services.UserService", "find") to
                setOf(MethodRef("com.example.domain.User", "getName")),
        )

        val deps = PackageDependencyBuilder.build(graph)

        val serviceDeps = deps.dependenciesOf("com.example.services")
        assertEquals(listOf("com.example.domain"), serviceDeps)
    }

    // [TEST-DONE] Extracts package dependencies from call graph

    @Test
    fun `self-dependencies within same package are excluded`() {
        val graph = buildCallGraph(
            MethodRef("com.example.services.A", "foo") to
                setOf(MethodRef("com.example.services.B", "bar")),
        )

        val deps = PackageDependencyBuilder.build(graph)

        assertTrue(deps.dependenciesOf("com.example.services").isEmpty())
    }

    // [TEST-DONE] Self-dependencies within the same package are excluded

    @Test
    fun `dependencies are deduplicated`() {
        val graph = buildCallGraph(
            MethodRef("com.example.services.A", "foo") to
                setOf(MethodRef("com.example.domain.X", "a")),
            MethodRef("com.example.services.B", "bar") to
                setOf(MethodRef("com.example.domain.Y", "b")),
        )

        val deps = PackageDependencyBuilder.build(graph)

        assertEquals(listOf("com.example.domain"), deps.dependenciesOf("com.example.services"))
    }

    // [TEST-DONE] Dependencies are deduplicated (multiple calls between same packages count once)

    @Test
    fun `dependencies are sorted alphabetically`() {
        val graph = buildCallGraph(
            MethodRef("com.example.services.Svc", "foo") to
                setOf(
                    MethodRef("com.example.zebra.Z", "a"),
                    MethodRef("com.example.alpha.A", "b"),
                    MethodRef("com.example.middle.M", "c"),
                ),
        )

        val deps = PackageDependencyBuilder.build(graph)

        assertEquals(
            listOf("com.example.alpha", "com.example.middle", "com.example.zebra"),
            deps.dependenciesOf("com.example.services"),
        )
    }

    // [TEST-DONE] Dependencies are sorted alphabetically

    @Test
    fun `packages with no outgoing dependencies have empty list`() {
        val graph = buildCallGraph(
            MethodRef("com.example.services.A", "foo") to
                setOf(MethodRef("com.example.domain.X", "a")),
        )

        val deps = PackageDependencyBuilder.build(graph)

        assertTrue(deps.dependenciesOf("com.example.domain").isEmpty())
    }

    // [TEST-DONE] Packages with no outgoing dependencies have empty dependency list

    @Test
    fun `findPackages matches pattern case-insensitively`() {
        val graph = buildCallGraph(
            MethodRef("com.example.services.A", "foo") to
                setOf(MethodRef("com.example.domain.X", "a")),
            MethodRef("com.example.ktor.routes.B", "bar") to
                setOf(MethodRef("com.example.services.C", "c")),
        )

        val deps = PackageDependencyBuilder.build(graph)

        val matches = deps.findPackages("services")
        assertEquals(listOf("com.example.services"), matches)
    }

    // [TEST-DONE] findPackages matches pattern case-insensitively

    @Test
    fun `filter removes external packages from dependencies`() {
        val graph = buildCallGraph(
            MethodRef("com.example.services.UserService", "find") to
                setOf(
                    MethodRef("com.example.domain.User", "getName"),
                    MethodRef("java.lang.Object", "toString"),
                    MethodRef("kotlin.jvm.internal.Intrinsics", "checkNotNullParameter"),
                ),
        )
        val projectClasses = setOf(
            "com.example.services.UserService",
            "com.example.domain.User",
        )

        val deps = PackageDependencyBuilder.build(graph, filter = { it.className in projectClasses })

        assertEquals(listOf("com.example.domain"), deps.dependenciesOf("com.example.services"))
        assertEquals(emptyList(), deps.findPackages("java"))
        assertEquals(emptyList(), deps.findPackages("kotlin"))
    }

    @Test
    fun `filter with all callees external yields no dependencies`() {
        val graph = buildCallGraph(
            MethodRef("com.example.services.Svc", "run") to
                setOf(
                    MethodRef("java.lang.String", "valueOf"),
                    MethodRef("kotlin.collections.CollectionsKt", "listOf"),
                ),
        )
        val projectClasses = setOf("com.example.services.Svc")

        val deps = PackageDependencyBuilder.build(graph, filter = { it.className in projectClasses })

        assertTrue(deps.dependenciesOf("com.example.services").isEmpty())
    }

    @Test
    fun `dependentsOf returns packages that depend on the given package`() {
        val graph = buildCallGraph(
            MethodRef("com.example.services.UserService", "find") to
                setOf(MethodRef("com.example.domain.User", "getName")),
            MethodRef("com.example.ktor.routes.UserRoute", "get") to
                setOf(MethodRef("com.example.domain.User", "toJson")),
        )

        val deps = PackageDependencyBuilder.build(graph)

        val dependents = deps.dependentsOf("com.example.domain")
        assertEquals(listOf("com.example.ktor.routes", "com.example.services"), dependents)
    }

    @Test
    fun `dependentsOf returns empty list for package with no dependents`() {
        val graph = buildCallGraph(
            MethodRef("com.example.services.Svc", "run") to
                setOf(MethodRef("com.example.domain.X", "a")),
        )

        val deps = PackageDependencyBuilder.build(graph)

        assertTrue(deps.dependentsOf("com.example.services").isEmpty())
    }

    @Test
    fun `dependentsOf results are sorted alphabetically`() {
        val graph = buildCallGraph(
            MethodRef("com.example.zebra.Z", "a") to
                setOf(MethodRef("com.example.target.T", "x")),
            MethodRef("com.example.alpha.A", "b") to
                setOf(MethodRef("com.example.target.T", "y")),
            MethodRef("com.example.middle.M", "c") to
                setOf(MethodRef("com.example.target.T", "z")),
        )

        val deps = PackageDependencyBuilder.build(graph)

        assertEquals(
            listOf("com.example.alpha", "com.example.middle", "com.example.zebra"),
            deps.dependentsOf("com.example.target"),
        )
    }

    private fun buildCallGraph(
        vararg edges: Pair<MethodRef, Set<MethodRef>>,
    ): CallGraph = CallGraph(edges.toMap())
}
