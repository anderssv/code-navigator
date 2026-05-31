package no.f12.codenavigator.navigation.deadcode

import no.f12.codenavigator.navigation.types.ClassName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class DeadCodeBaselineDiffTest {

    @Test
    fun `items in baseline but not in current are removed`() {
        val baseline = listOf(deadClass("com.example.Orphan"))
        val current = emptyList<DeadCode>()

        val diff = DeadCodeBaselineDiff.compare(baseline, current)

        assertEquals(listOf(deadClass("com.example.Orphan")), diff.removed)
        assertEquals(emptyList(), diff.remaining)
        assertEquals(emptyList(), diff.new)
    }

    @Test
    fun `items in both baseline and current are remaining`() {
        val item = deadClass("com.example.Orphan")
        val baseline = listOf(item)
        val current = listOf(item)

        val diff = DeadCodeBaselineDiff.compare(baseline, current)

        assertEquals(emptyList(), diff.removed)
        assertEquals(listOf(item), diff.remaining)
        assertEquals(emptyList(), diff.new)
    }

    @Test
    fun `items in current but not in baseline are new`() {
        val baseline = emptyList<DeadCode>()
        val current = listOf(deadClass("com.example.New"))

        val diff = DeadCodeBaselineDiff.compare(baseline, current)

        assertEquals(emptyList(), diff.removed)
        assertEquals(emptyList(), diff.remaining)
        assertEquals(listOf(deadClass("com.example.New")), diff.new)
    }

    @Test
    fun `empty baseline and empty current produces empty diff`() {
        val diff = DeadCodeBaselineDiff.compare(emptyList(), emptyList())

        assertEquals(emptyList(), diff.removed)
        assertEquals(emptyList(), diff.remaining)
        assertEquals(emptyList(), diff.new)
    }

    @Test
    fun `matching uses className plus memberName plus kind as identity`() {
        val baselineMethod = deadMethod("com.example.Service", "foo")
        val currentMethod = deadMethod("com.example.Service", "foo").copy(confidence = DeadCodeConfidence.MEDIUM)

        val diff = DeadCodeBaselineDiff.compare(listOf(baselineMethod), listOf(currentMethod))

        assertEquals(emptyList(), diff.removed)
        assertEquals(listOf(currentMethod), diff.remaining)
        assertEquals(emptyList(), diff.new)
    }

    @Test
    fun `full scenario with removed remaining and new items`() {
        val baseline = listOf(
            deadClass("com.example.Removed"),
            deadClass("com.example.Still"),
            deadMethod("com.example.Service", "oldMethod"),
        )
        val current = listOf(
            deadClass("com.example.Still"),
            deadMethod("com.example.Service", "newMethod"),
        )

        val diff = DeadCodeBaselineDiff.compare(baseline, current)

        assertEquals(listOf(deadClass("com.example.Removed"), deadMethod("com.example.Service", "oldMethod")), diff.removed)
        assertEquals(listOf(deadClass("com.example.Still")), diff.remaining)
        assertEquals(listOf(deadMethod("com.example.Service", "newMethod")), diff.new)
    }

    private fun deadClass(name: String) = DeadCode(
        className = ClassName(name),
        memberName = null,
        kind = DeadCodeKind.CLASS,
        sourceFile = "${name.substringAfterLast('.')}.kt",
        confidence = DeadCodeConfidence.HIGH,
        reason = DeadCodeReason.NO_REFERENCES,
    )

    @Test
    fun `parses baseline JSON into DeadCode list`() {
        val json = """[
            {"className":"com.example.Orphan","memberName":null,"kind":"class","sourceFile":"Orphan.kt","confidence":"high","reason":"no_references"},
            {"className":"com.example.Service","memberName":"unused","kind":"method","sourceFile":"Service.kt","confidence":"medium","reason":"test_only"}
        ]"""

        val parsed = DeadCodeBaselineDiff.parseBaseline(json)

        assertEquals(2, parsed.size)
        assertEquals(ClassName("com.example.Orphan"), parsed[0].className)
        assertEquals(null, parsed[0].memberName)
        assertEquals(DeadCodeKind.CLASS, parsed[0].kind)
        assertEquals(DeadCodeConfidence.HIGH, parsed[0].confidence)
        assertEquals(DeadCodeReason.NO_REFERENCES, parsed[0].reason)
        assertEquals("unused", parsed[1].memberName)
        assertEquals(DeadCodeKind.METHOD, parsed[1].kind)
        assertEquals(DeadCodeConfidence.MEDIUM, parsed[1].confidence)
        assertEquals(DeadCodeReason.TEST_ONLY, parsed[1].reason)
    }

    @Test
    fun `formatDiff shows summary and sections`() {
        val diff = DeadCodeDiff(
            removed = listOf(deadClass("com.example.Removed")),
            remaining = listOf(deadClass("com.example.Still")),
            new = listOf(deadMethod("com.example.Service", "newMethod")),
        )

        val output = DeadCodeBaselineDiffFormatter.format(diff)

        assertTrue(output.contains("Removed (1)"), "Should show removed count")
        assertTrue(output.contains("com.example.Removed"), "Should list removed item")
        assertTrue(output.contains("Remaining (1)"), "Should show remaining count")
        assertTrue(output.contains("com.example.Still"), "Should list remaining item")
        assertTrue(output.contains("New (1)"), "Should show new count")
        assertTrue(output.contains("com.example.Service.newMethod"), "Should list new item")
    }

    @Test
    fun `formatDiff omits empty sections`() {
        val diff = DeadCodeDiff(
            removed = listOf(deadClass("com.example.Removed")),
            remaining = emptyList(),
            new = emptyList(),
        )

        val output = DeadCodeBaselineDiffFormatter.format(diff)

        assertTrue(output.contains("Removed (1)"))
        assertFalse(output.contains("Remaining"))
        assertFalse(output.contains("New"))
    }

    private fun deadMethod(className: String, method: String) = DeadCode(
        className = ClassName(className),
        memberName = method,
        kind = DeadCodeKind.METHOD,
        sourceFile = "${className.substringAfterLast('.')}.kt",
        confidence = DeadCodeConfidence.HIGH,
        reason = DeadCodeReason.NO_REFERENCES,
    )
}
