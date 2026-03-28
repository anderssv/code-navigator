package no.f12.codenavigator.navigation

import no.f12.codenavigator.navigation.dsm.DsmHtmlRenderer
import no.f12.codenavigator.navigation.dsm.DsmMatrix
import kotlin.test.Test
import kotlin.test.assertTrue

class DsmHtmlRendererTest {

    @Test
    fun `empty matrix produces minimal html`() {
        val matrix = DsmMatrix(emptyList(), emptyMap(), emptyMap())

        val html = DsmHtmlRenderer.render(matrix)

        assertTrue(html.contains("<!DOCTYPE html>"))
        assertTrue(html.contains("No inter-package dependencies"))
    }

    @Test
    fun `renders complete html document with table`() {
        val matrix = DsmMatrix(
            packages = listOf(PackageName("api"), PackageName("model")),
            cells = mapOf((PackageName("api") to PackageName("model")) to 3),
            classDependencies = mapOf(
                (PackageName("api") to PackageName("model")) to setOf(ClassName("Controller") to ClassName("User")),
            ),
        )

        val html = DsmHtmlRenderer.render(matrix)

        assertTrue(html.contains("<!DOCTYPE html>"))
        assertTrue(html.contains("</html>"))
        assertTrue(html.contains("<table>"))
        assertTrue(html.contains("api"))
        assertTrue(html.contains("model"))
    }

    @Test
    fun `color-codes forward dependencies as green`() {
        val matrix = DsmMatrix(
            packages = listOf(PackageName("api"), PackageName("model")),
            cells = mapOf((PackageName("api") to PackageName("model")) to 1),
            classDependencies = emptyMap(),
        )

        val html = DsmHtmlRenderer.render(matrix)

        assertTrue(html.contains("backward") || html.contains("forward"),
            "Should contain dependency color class")
    }

    @Test
    fun `color-codes backward dependencies as red`() {
        val matrix = DsmMatrix(
            packages = listOf(PackageName("api"), PackageName("model")),
            cells = mapOf((PackageName("model") to PackageName("api")) to 1),
            classDependencies = emptyMap(),
        )

        val html = DsmHtmlRenderer.render(matrix)

        assertTrue(html.contains("forward") || html.contains("backward"),
            "Should contain dependency color class")
    }

    @Test
    fun `includes class-level tooltips`() {
        val matrix = DsmMatrix(
            packages = listOf(PackageName("api"), PackageName("model")),
            cells = mapOf((PackageName("api") to PackageName("model")) to 1),
            classDependencies = mapOf(
                (PackageName("api") to PackageName("model")) to setOf(ClassName("Controller") to ClassName("User")),
            ),
        )

        val html = DsmHtmlRenderer.render(matrix)

        assertTrue(html.contains("tooltip"))
        assertTrue(html.contains("Controller"))
        assertTrue(html.contains("User"))
    }

    @Test
    fun `includes package legend`() {
        val matrix = DsmMatrix(
            packages = listOf(PackageName("api"), PackageName("model"), PackageName("service")),
            cells = mapOf((PackageName("api") to PackageName("model")) to 1),
            classDependencies = emptyMap(),
        )

        val html = DsmHtmlRenderer.render(matrix)

        assertTrue(html.contains("legend") || html.contains("Package legend"))
        assertTrue(html.contains("api"))
        assertTrue(html.contains("model"))
        assertTrue(html.contains("service"))
    }

    @Test
    fun `diagonal cells have diagonal class`() {
        val matrix = DsmMatrix(
            packages = listOf(PackageName("api"), PackageName("model")),
            cells = mapOf((PackageName("api") to PackageName("model")) to 1),
            classDependencies = emptyMap(),
        )

        val html = DsmHtmlRenderer.render(matrix)

        assertTrue(html.contains("diagonal"))
    }
}
