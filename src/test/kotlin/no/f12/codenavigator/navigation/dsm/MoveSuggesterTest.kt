package no.f12.codenavigator.navigation.dsm

import no.f12.codenavigator.navigation.types.ClassName
import no.f12.codenavigator.navigation.types.PackageName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MoveSuggesterTest {

    @Test
    fun `empty dependencies produces no move suggestions`() {
        val result = MoveSuggester.suggest(emptyList())

        assertTrue(result.suggestions.isEmpty())
    }

    // [TEST] Class with all edges to own package produces no suggestion
    @Test
    fun `class with all edges to own package produces no suggestion`() {
        val deps = listOf(
            PackageDependency(PackageName("com.a"), PackageName("com.a"), ClassName("com.a.Foo"), ClassName("com.a.Bar")),
            PackageDependency(PackageName("com.a"), PackageName("com.a"), ClassName("com.a.Foo"), ClassName("com.a.Baz")),
        )

        val result = MoveSuggester.suggest(deps)

        assertTrue(result.suggestions.isEmpty())
    }

    // [TEST] Class with more edges to another package than own produces a move suggestion
    @Test
    fun `class with more edges to another package than own produces a move suggestion`() {
        val deps = listOf(
            PackageDependency(PackageName("com.a"), PackageName("com.a"), ClassName("com.a.Foo"), ClassName("com.a.Bar")),
            PackageDependency(PackageName("com.a"), PackageName("com.b"), ClassName("com.a.Foo"), ClassName("com.b.X")),
            PackageDependency(PackageName("com.a"), PackageName("com.b"), ClassName("com.a.Foo"), ClassName("com.b.Y")),
            PackageDependency(PackageName("com.a"), PackageName("com.b"), ClassName("com.a.Foo"), ClassName("com.b.Z")),
        )

        val result = MoveSuggester.suggest(deps)

        assertEquals(1, result.suggestions.size)
        val suggestion = result.suggestions[0]
        assertEquals(ClassName("com.a.Foo"), suggestion.className)
        assertEquals(PackageName("com.a"), suggestion.currentPackage)
        assertEquals(PackageName("com.b"), suggestion.suggestedPackage)
        assertEquals(1, suggestion.edgesToCurrent)
        assertEquals(3, suggestion.edgesToSuggested)
    }

    // [TEST] Class with equal edges to own and another package produces no suggestion (tie = stay)
    @Test
    fun `class with equal edges to own and another package produces no suggestion`() {
        val deps = listOf(
            PackageDependency(PackageName("com.a"), PackageName("com.a"), ClassName("com.a.Foo"), ClassName("com.a.Bar")),
            PackageDependency(PackageName("com.a"), PackageName("com.b"), ClassName("com.a.Foo"), ClassName("com.b.X")),
        )

        val result = MoveSuggester.suggest(deps)

        assertTrue(result.suggestions.isEmpty())
    }

    @Test
    fun `class with zero edges to own package and all to one other package suggests move`() {
        val deps = listOf(
            PackageDependency(PackageName("com.a"), PackageName("com.b"), ClassName("com.a.Foo"), ClassName("com.b.X")),
            PackageDependency(PackageName("com.a"), PackageName("com.b"), ClassName("com.a.Foo"), ClassName("com.b.Y")),
        )

        val result = MoveSuggester.suggest(deps)

        assertEquals(1, result.suggestions.size)
        assertEquals(ClassName("com.a.Foo"), result.suggestions[0].className)
        assertEquals(PackageName("com.b"), result.suggestions[0].suggestedPackage)
        assertEquals(0, result.suggestions[0].edgesToCurrent)
        assertEquals(2, result.suggestions[0].edgesToSuggested)
        assertEquals(1.0, result.suggestions[0].confidence)
    }

    @Test
    fun `multiple classes can produce multiple suggestions`() {
        val deps = listOf(
            PackageDependency(PackageName("com.a"), PackageName("com.b"), ClassName("com.a.Foo"), ClassName("com.b.X")),
            PackageDependency(PackageName("com.a"), PackageName("com.b"), ClassName("com.a.Foo"), ClassName("com.b.Y")),
            PackageDependency(PackageName("com.c"), PackageName("com.b"), ClassName("com.c.Bar"), ClassName("com.b.X")),
            PackageDependency(PackageName("com.c"), PackageName("com.b"), ClassName("com.c.Bar"), ClassName("com.b.Z")),
        )

        val result = MoveSuggester.suggest(deps)

        assertEquals(2, result.suggestions.size)
    }

    @Test
    fun `ubiquitous types with high fan-in are not suggested for move`() {
        val deps = listOf(
            // Foo depends on Ubiquitous (which many classes depend on)
            PackageDependency(PackageName("com.a"), PackageName("com.util"), ClassName("com.a.Foo"), ClassName("com.util.Ubiquitous")),
            PackageDependency(PackageName("com.b"), PackageName("com.util"), ClassName("com.b.Bar"), ClassName("com.util.Ubiquitous")),
            PackageDependency(PackageName("com.c"), PackageName("com.util"), ClassName("com.c.Baz"), ClassName("com.util.Ubiquitous")),
            PackageDependency(PackageName("com.d"), PackageName("com.util"), ClassName("com.d.Qux"), ClassName("com.util.Ubiquitous")),
            PackageDependency(PackageName("com.e"), PackageName("com.util"), ClassName("com.e.Quux"), ClassName("com.util.Ubiquitous")),
            // Foo also has 1 edge to own package
            PackageDependency(PackageName("com.a"), PackageName("com.a"), ClassName("com.a.Foo"), ClassName("com.a.Other")),
        )

        val result = MoveSuggester.suggest(deps, maxFanIn = 4)

        // Foo should NOT be suggested to move to com.util because Ubiquitous is ubiquitous
        assertTrue(result.suggestions.none { it.className == ClassName("com.a.Foo") })
    }

    @Test
    fun `suggestions are sorted by confidence descending`() {
        val deps = listOf(
            // Foo: 1 own, 2 other → confidence 2/3
            PackageDependency(PackageName("com.a"), PackageName("com.a"), ClassName("com.a.Foo"), ClassName("com.a.X")),
            PackageDependency(PackageName("com.a"), PackageName("com.b"), ClassName("com.a.Foo"), ClassName("com.b.Y")),
            PackageDependency(PackageName("com.a"), PackageName("com.b"), ClassName("com.a.Foo"), ClassName("com.b.Z")),
            // Bar: 0 own, 3 other → confidence 1.0
            PackageDependency(PackageName("com.c"), PackageName("com.d"), ClassName("com.c.Bar"), ClassName("com.d.A")),
            PackageDependency(PackageName("com.c"), PackageName("com.d"), ClassName("com.c.Bar"), ClassName("com.d.B")),
            PackageDependency(PackageName("com.c"), PackageName("com.d"), ClassName("com.c.Bar"), ClassName("com.d.C")),
        )

        val result = MoveSuggester.suggest(deps)

        assertEquals(ClassName("com.c.Bar"), result.suggestions[0].className)
        assertEquals(ClassName("com.a.Foo"), result.suggestions[1].className)
    }

    @Test
    fun `top parameter limits output count`() {
        val deps = listOf(
            PackageDependency(PackageName("com.a"), PackageName("com.b"), ClassName("com.a.Foo"), ClassName("com.b.X")),
            PackageDependency(PackageName("com.a"), PackageName("com.b"), ClassName("com.a.Foo"), ClassName("com.b.Y")),
            PackageDependency(PackageName("com.c"), PackageName("com.d"), ClassName("com.c.Bar"), ClassName("com.d.A")),
            PackageDependency(PackageName("com.c"), PackageName("com.d"), ClassName("com.c.Bar"), ClassName("com.d.B")),
        )

        val result = MoveSuggester.suggest(deps, top = 1)

        assertEquals(1, result.suggestions.size)
    }
}
