package no.f12.codenavigator.navigation

import no.f12.codenavigator.navigation.callgraph.MethodRef
import no.f12.codenavigator.navigation.annotation.AnnotationMatch
import no.f12.codenavigator.navigation.annotation.AnnotationQueryFormatter
import no.f12.codenavigator.navigation.annotation.MethodAnnotationMatch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AnnotationQueryFormatterTest {

    @Test
    fun `formats single class match with annotations`() {
        val matches = listOf(
            AnnotationMatch(
                className = ClassName("com.example.MyController"),
                sourceFile = "MyController.kt",
                classAnnotations = setOf(AnnotationName("RestController")),
                matchedMethods = emptyList(),
            ),
        )

        val result = AnnotationQueryFormatter.format(matches)

        assertEquals(
            "com.example.MyController (MyController.kt) [@RestController]",
            result,
        )
    }

    @Test
    fun `formats class with multiple annotations sorted`() {
        val matches = listOf(
            AnnotationMatch(
                className = ClassName("com.example.MyController"),
                sourceFile = "MyController.kt",
                classAnnotations = setOf(AnnotationName("RestController"), AnnotationName("RequestMapping")),
                matchedMethods = emptyList(),
            ),
        )

        val result = AnnotationQueryFormatter.format(matches)

        assertEquals(
            "com.example.MyController (MyController.kt) [@RequestMapping, @RestController]",
            result,
        )
    }

    @Test
    fun `formats class with method matches`() {
        val matches = listOf(
            AnnotationMatch(
                className = ClassName("com.example.MyController"),
                sourceFile = "MyController.kt",
                classAnnotations = setOf(AnnotationName("RestController")),
                matchedMethods = listOf(
                    MethodAnnotationMatch(
                        method = MethodRef(ClassName("com.example.MyController"), "getUsers"),
                        annotations = setOf(AnnotationName("GetMapping")),
                    ),
                ),
            ),
        )

        val result = AnnotationQueryFormatter.format(matches)

        val expected = """
            com.example.MyController (MyController.kt) [@RestController]
              getUsers [@GetMapping]
        """.trimIndent()
        assertEquals(expected, result)
    }

    @Test
    fun `formats multiple classes separated by blank line`() {
        val matches = listOf(
            AnnotationMatch(
                className = ClassName("com.example.ControllerA"),
                sourceFile = "ControllerA.kt",
                classAnnotations = setOf(AnnotationName("RestController")),
                matchedMethods = emptyList(),
            ),
            AnnotationMatch(
                className = ClassName("com.example.ControllerB"),
                sourceFile = "ControllerB.kt",
                classAnnotations = setOf(AnnotationName("RestController")),
                matchedMethods = emptyList(),
            ),
        )

        val result = AnnotationQueryFormatter.format(matches)

        val expected = """
            com.example.ControllerA (ControllerA.kt) [@RestController]
            com.example.ControllerB (ControllerB.kt) [@RestController]
        """.trimIndent()
        assertEquals(expected, result)
    }

    @Test
    fun `formats empty results`() {
        val result = AnnotationQueryFormatter.format(emptyList())

        assertEquals("No matching annotations found.", result)
    }

    @Test
    fun `formats class with no class annotations but method matches`() {
        val matches = listOf(
            AnnotationMatch(
                className = ClassName("com.example.Plain"),
                sourceFile = "Plain.kt",
                classAnnotations = emptySet(),
                matchedMethods = listOf(
                    MethodAnnotationMatch(
                        method = MethodRef(ClassName("com.example.Plain"), "scheduled"),
                        annotations = setOf(AnnotationName("Scheduled")),
                    ),
                ),
            ),
        )

        val result = AnnotationQueryFormatter.format(matches)

        val expected = """
            com.example.Plain (Plain.kt)
              scheduled [@Scheduled]
        """.trimIndent()
        assertEquals(expected, result)
    }

    @Test
    fun `noResultsGuidance hints about methods flag when methods is false`() {
        val result = AnnotationQueryFormatter.noResultsGuidance("Test", methods = false)

        assertTrue(result.contains("No annotations matching 'Test' found."))
        assertTrue(result.contains("-Pmethods=true"))
        assertTrue(result.contains("SOURCE retention"))
    }

    @Test
    fun `noResultsGuidance omits methods hint when methods is true`() {
        val result = AnnotationQueryFormatter.noResultsGuidance("Test", methods = true)

        assertTrue(result.contains("No annotations matching 'Test' found."))
        assertTrue(!result.contains("-Pmethods=true"))
        assertTrue(result.contains("SOURCE retention"))
    }
}
