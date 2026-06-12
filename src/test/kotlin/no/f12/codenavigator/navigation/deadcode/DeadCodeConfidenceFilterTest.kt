package no.f12.codenavigator.navigation.deadcode

import no.f12.codenavigator.navigation.types.ClassName
import kotlin.test.Test
import kotlin.test.assertEquals

class DeadCodeConfidenceFilterTest {

    private fun finding(name: String, confidence: DeadCodeConfidence) = DeadCode(
        className = ClassName("com.app.$name"),
        memberName = null,
        kind = DeadCodeKind.CLASS,
        sourceFile = "$name.kt",
        confidence = confidence,
        reason = DeadCodeReason.NO_REFERENCES,
    )

    private val findings = listOf(
        finding("HighOne", DeadCodeConfidence.HIGH),
        finding("MediumOne", DeadCodeConfidence.MEDIUM),
        finding("LowOne", DeadCodeConfidence.LOW),
    )

    @Test
    fun `min-confidence LOW keeps everything`() {
        val result = DeadCodeOrchestrator.filterByConfidence(findings, DeadCodeConfidence.LOW)

        assertEquals(3, result.size)
    }

    @Test
    fun `min-confidence MEDIUM drops LOW`() {
        val result = DeadCodeOrchestrator.filterByConfidence(findings, DeadCodeConfidence.MEDIUM)

        assertEquals(setOf(DeadCodeConfidence.HIGH, DeadCodeConfidence.MEDIUM), result.map { it.confidence }.toSet())
    }

    @Test
    fun `min-confidence HIGH keeps only HIGH`() {
        val result = DeadCodeOrchestrator.filterByConfidence(findings, DeadCodeConfidence.HIGH)

        assertEquals(listOf(DeadCodeConfidence.HIGH), result.map { it.confidence })
    }

    @Test
    fun `parse maps strings to threshold and defaults to LOW`() {
        assertEquals(DeadCodeConfidence.HIGH, DeadCodeConfidence.parse("high"))
        assertEquals(DeadCodeConfidence.MEDIUM, DeadCodeConfidence.parse("MEDIUM"))
        assertEquals(DeadCodeConfidence.LOW, DeadCodeConfidence.parse("low"))
        assertEquals(DeadCodeConfidence.LOW, DeadCodeConfidence.parse(null))
        assertEquals(DeadCodeConfidence.LOW, DeadCodeConfidence.parse("garbage"))
    }
}
