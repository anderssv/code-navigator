package no.f12.codenavigator.navigation.stringconstant

import no.f12.codenavigator.navigation.stringconstant.StringConstantScanner
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class StringConstantScannerTest {

    private val testProjectClasses = File("test-project/build/classes/kotlin/main")

    @Test
    fun `scans directory and filters by pattern`() {
        val result = StringConstantScanner.scan(listOf(testProjectClasses), Regex("/api/"))

        assertTrue(result.data.isNotEmpty(), "Should find /api/ strings")
        assertTrue(result.data.all { "/api/" in it.value })
    }

    @Test
    fun `returns empty result for non-existent directory`() {
        val result = StringConstantScanner.scan(
            listOf(File("test-project/build/classes/does-not-exist")),
            Regex("anything"),
        )

        assertTrue(result.data.isEmpty())
    }

    @Test
    fun `pattern filters correctly`() {
        val result = StringConstantScanner.scan(listOf(testProjectClasses), Regex("/health"))

        assertTrue(result.data.isNotEmpty(), "Should find /health string")
        assertTrue(result.data.all { "/health" in it.value })
    }

    @Test
    fun `results are sorted by class name then method name then value`() {
        val result = StringConstantScanner.scan(listOf(testProjectClasses), Regex("/api/"))

        val sortKeys = result.data.map { Triple(it.className.value, it.methodName, it.value) }
        val sorted = sortKeys.sortedWith(compareBy({ it.first }, { it.second }, { it.third }))
        assertTrue(sortKeys == sorted, "Results should be sorted")
    }

    @Test
    fun `no results for pattern that matches nothing`() {
        val result = StringConstantScanner.scan(listOf(testProjectClasses), Regex("xyzNonExistent123"))

        assertTrue(result.data.isEmpty())
    }
}
