package no.f12.codenavigator.navigation

import no.f12.codenavigator.navigation.core.ClassName
import no.f12.codenavigator.navigation.deadcode.DeadCode
import no.f12.codenavigator.navigation.deadcode.DeadCodeConfidence
import no.f12.codenavigator.navigation.deadcode.DeadCodeFormatter
import no.f12.codenavigator.navigation.deadcode.DeadCodeKind
import no.f12.codenavigator.navigation.deadcode.DeadCodeReason
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
            DeadCode(ClassName("com.example.Orphan"), null, DeadCodeKind.CLASS, "Orphan.kt", DeadCodeConfidence.HIGH, DeadCodeReason.NO_REFERENCES),
            DeadCode(ClassName("com.example.Service"), "unused", DeadCodeKind.METHOD, "Service.kt", DeadCodeConfidence.HIGH, DeadCodeReason.NO_REFERENCES),
        )

        val output = DeadCodeFormatter.format(dead)

        assertTrue(output.contains("Class"), "Should contain Class header")
        assertTrue(output.contains("Member"), "Should contain Member header")
        assertTrue(output.contains("Kind"), "Should contain Kind header")
        assertTrue(output.contains("Source"), "Should contain Source header")
        assertTrue(output.contains("Confidence"), "Should contain Confidence header")
        assertTrue(output.contains("Reason"), "Should contain Reason header")
        assertTrue(output.contains("com.example.Orphan"), "Should contain dead class name")
        assertTrue(output.contains("com.example.Service"), "Should contain method's class name")
        assertTrue(output.contains("unused"), "Should contain dead method name")
        assertTrue(output.contains("CLASS"), "Should contain kind CLASS")
        assertTrue(output.contains("METHOD"), "Should contain kind METHOD")
        assertTrue(output.contains("HIGH"), "Should contain confidence HIGH")
        assertTrue(output.contains("NO_REFERENCES"), "Should contain reason NO_REFERENCES")
    }

    @Test
    fun `class entries show dash for member column`() {
        val dead = listOf(
            DeadCode(ClassName("com.example.Orphan"), null, DeadCodeKind.CLASS, "Orphan.kt", DeadCodeConfidence.HIGH, DeadCodeReason.NO_REFERENCES),
        )

        val output = DeadCodeFormatter.format(dead)

        assertTrue(output.contains("-"), "Class entries show dash for member name")
    }

    @Test
    fun `includes note about exclude regex at the end`() {
        val dead = listOf(
            DeadCode(ClassName("com.example.Orphan"), null, DeadCodeKind.CLASS, "Orphan.kt", DeadCodeConfidence.HIGH, DeadCodeReason.NO_REFERENCES),
        )

        val output = DeadCodeFormatter.format(dead)

        assertTrue(output.contains("exclude=<regex>"), "Should contain hint about exclude parameter")
    }

    @Test
    fun `no-results message does not include note`() {
        val output = DeadCodeFormatter.format(emptyList())

        assertTrue(!output.contains("exclude"), "No-results message should not include the note")
    }
}
