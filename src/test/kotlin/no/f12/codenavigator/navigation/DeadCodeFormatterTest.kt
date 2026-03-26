package no.f12.codenavigator.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DeadCodeFormatterTest {

    @Test
    fun `empty list produces no-results message`() {
        val output = DeadCodeFormatter.format(emptyList())

        assertEquals("No potential dead code found.", output)
    }

    @Test
    fun `formats dead classes and methods as columnar table`() {
        val dead = listOf(
            DeadCode(ClassName("com.example.Orphan"), null, DeadCodeKind.CLASS, "Orphan.kt"),
            DeadCode(ClassName("com.example.Service"), "unused", DeadCodeKind.METHOD, "Service.kt"),
        )

        val output = DeadCodeFormatter.format(dead)

        assertTrue(output.contains("Class"), "Should contain Class header")
        assertTrue(output.contains("Member"), "Should contain Member header")
        assertTrue(output.contains("Kind"), "Should contain Kind header")
        assertTrue(output.contains("Source"), "Should contain Source header")
        assertTrue(output.contains("com.example.Orphan"), "Should contain dead class name")
        assertTrue(output.contains("com.example.Service"), "Should contain method's class name")
        assertTrue(output.contains("unused"), "Should contain dead method name")
        assertTrue(output.contains("CLASS"), "Should contain kind CLASS")
        assertTrue(output.contains("METHOD"), "Should contain kind METHOD")
    }

    @Test
    fun `class entries show dash for member column`() {
        val dead = listOf(
            DeadCode(ClassName("com.example.Orphan"), null, DeadCodeKind.CLASS, "Orphan.kt"),
        )

        val output = DeadCodeFormatter.format(dead)

        assertTrue(output.contains("-"), "Class entries show dash for member name")
    }
}
