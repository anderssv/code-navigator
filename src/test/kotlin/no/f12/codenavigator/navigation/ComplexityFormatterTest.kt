package no.f12.codenavigator.navigation

import no.f12.codenavigator.navigation.complexity.ClassComplexity
import no.f12.codenavigator.navigation.complexity.ComplexityFormatter
import kotlin.test.Test
import kotlin.test.assertEquals

class ComplexityFormatterTest {

    @Test
    fun `empty list produces no-results message`() {
        val result = ComplexityFormatter.format(emptyList())

        assertEquals("No matching classes found.", result)
    }

    @Test
    fun `formats single class summary`() {
        val complexity = ClassComplexity(
            className = ClassName("com.example.Service"),
            sourceFile = "Service.kt",
            fanOut = 5,
            fanIn = 3,
            distinctOutgoingClasses = 2,
            distinctIncomingClasses = 2,
            outgoingByClass = listOf(ClassName("com.example.Repo") to 3, ClassName("com.example.Cache") to 2),
            incomingByClass = listOf(ClassName("com.example.Controller") to 2, ClassName("com.example.Scheduler") to 1),
        )

        val result = ComplexityFormatter.format(listOf(complexity))

        val expected = buildString {
            appendLine("com.example.Service (Service.kt)")
            appendLine("  Fan-out: 5 calls to 2 distinct classes")
            appendLine("  Fan-in:  3 calls from 2 distinct classes")
            appendLine("  Top outgoing: com.example.Repo (3), com.example.Cache (2)")
            append("  Top incoming: com.example.Controller (2), com.example.Scheduler (1)")
        }
        assertEquals(expected, result)
    }

    @Test
    fun `formats class with no outgoing calls`() {
        val complexity = ClassComplexity(
            className = ClassName("com.example.Leaf"),
            sourceFile = "Leaf.kt",
            fanOut = 0,
            fanIn = 2,
            distinctOutgoingClasses = 0,
            distinctIncomingClasses = 1,
            outgoingByClass = emptyList(),
            incomingByClass = listOf(ClassName("com.example.Service") to 2),
        )

        val result = ComplexityFormatter.format(listOf(complexity))

        val expected = buildString {
            appendLine("com.example.Leaf (Leaf.kt)")
            appendLine("  Fan-out: 0 calls to 0 distinct classes")
            appendLine("  Fan-in:  2 calls from 1 distinct classes")
            appendLine("  Top outgoing: (none)")
            append("  Top incoming: com.example.Service (2)")
        }
        assertEquals(expected, result)
    }

    @Test
    fun `formats multiple classes separated by blank line`() {
        val c1 = ClassComplexity(
            className = ClassName("com.example.A"),
            sourceFile = "A.kt",
            fanOut = 1,
            fanIn = 0,
            distinctOutgoingClasses = 1,
            distinctIncomingClasses = 0,
            outgoingByClass = listOf(ClassName("com.example.B") to 1),
            incomingByClass = emptyList(),
        )
        val c2 = ClassComplexity(
            className = ClassName("com.example.B"),
            sourceFile = "B.kt",
            fanOut = 0,
            fanIn = 1,
            distinctOutgoingClasses = 0,
            distinctIncomingClasses = 1,
            outgoingByClass = emptyList(),
            incomingByClass = listOf(ClassName("com.example.A") to 1),
        )

        val result = ComplexityFormatter.format(listOf(c1, c2))

        val expected = buildString {
            appendLine("com.example.A (A.kt)")
            appendLine("  Fan-out: 1 calls to 1 distinct classes")
            appendLine("  Fan-in:  0 calls from 0 distinct classes")
            appendLine("  Top outgoing: com.example.B (1)")
            appendLine("  Top incoming: (none)")
            appendLine()
            appendLine("com.example.B (B.kt)")
            appendLine("  Fan-out: 0 calls to 0 distinct classes")
            appendLine("  Fan-in:  1 calls from 1 distinct classes")
            appendLine("  Top outgoing: (none)")
            append("  Top incoming: com.example.A (1)")
        }
        assertEquals(expected, result)
    }
}
