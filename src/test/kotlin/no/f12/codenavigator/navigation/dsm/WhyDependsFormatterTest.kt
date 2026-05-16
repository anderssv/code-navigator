package no.f12.codenavigator.navigation.dsm

import no.f12.codenavigator.navigation.types.ClassName
import no.f12.codenavigator.navigation.types.PackageName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertContains

class WhyDependsFormatterTest {

    private val fromPkg = PackageName("com.example.api")
    private val toPkg = PackageName("com.example.db")

    @Test
    fun `formats single edge`() {
        val result = WhyDependsResult(
            fromPackage = fromPkg,
            toPackage = toPkg,
            edges = listOf(WhyDependsEdge(ClassName("com.example.api.Controller"), ClassName("com.example.db.Repo"), 1)),
        )

        val text = WhyDependsFormatter.format(result)

        assertContains(text, "Controller")
        assertContains(text, "Repo")
        assertContains(text, "→")
    }

    @Test
    fun `formats multiple edges sorted by count descending`() {
        val result = WhyDependsResult(
            fromPackage = fromPkg,
            toPackage = toPkg,
            edges = listOf(
                WhyDependsEdge(ClassName("com.example.api.Controller"), ClassName("com.example.db.Repo"), 3),
                WhyDependsEdge(ClassName("com.example.api.Handler"), ClassName("com.example.db.Connection"), 1),
            ),
        )

        val text = WhyDependsFormatter.format(result)
        val lines = text.lines()

        // First edge should have higher count
        val controllerLine = lines.indexOfFirst { it.contains("Controller") }
        val handlerLine = lines.indexOfFirst { it.contains("Handler") }
        assert(controllerLine < handlerLine) { "Higher-count edge should appear first" }
    }

    @Test
    fun `shows edge count when greater than 1`() {
        val result = WhyDependsResult(
            fromPackage = fromPkg,
            toPackage = toPkg,
            edges = listOf(WhyDependsEdge(ClassName("com.example.api.Controller"), ClassName("com.example.db.Repo"), 3)),
        )

        val text = WhyDependsFormatter.format(result)

        assertContains(text, "3")
    }

    @Test
    fun `noResultsHints suggests checking package names`() {
        val hints = WhyDependsFormatter.noResultsHints(fromPkg, toPkg)

        assert(hints.isNotEmpty())
    }
}
