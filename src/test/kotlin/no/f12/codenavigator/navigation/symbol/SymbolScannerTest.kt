package no.f12.codenavigator.navigation.symbol

import no.f12.codenavigator.navigation.symbol.SymbolKind
import no.f12.codenavigator.navigation.symbol.SymbolScanner
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class SymbolScannerTest {

    private val testProjectClasses = File("test-project/build/classes/kotlin/main")

    @Test
    fun `scans directory and finds symbols from class files`() {
        val results = SymbolScanner.scan(listOf(testProjectClasses)).data

        assertTrue(results.size > 20, "Should find many symbols in test-project, got: ${results.size}")
        assertTrue(results.any { it.kind == SymbolKind.METHOD })
        assertTrue(results.any { it.kind == SymbolKind.FIELD })
    }

    @Test
    fun `returns empty list for empty directory`() {
        val emptyDir = File("test-project/build/classes/kotlin/main/com/example/nonexistent")

        val results = SymbolScanner.scan(listOf(emptyDir)).data

        assertTrue(results.isEmpty())
    }

    @Test
    fun `results are sorted by package then class then symbol name`() {
        val results = SymbolScanner.scan(listOf(testProjectClasses)).data

        val sortKeys = results.map { Triple(it.packageName.value, it.className.value, it.symbolName) }
        val sorted = sortKeys.sortedWith(compareBy({ it.first }, { it.second }, { it.third }))
        assertTrue(sortKeys == sorted, "Results should be sorted")
    }

    @Test
    fun `handles non-existent directory gracefully`() {
        val nonExistent = File("test-project/build/classes/does-not-exist")

        val results = SymbolScanner.scan(listOf(nonExistent)).data

        assertTrue(results.isEmpty())
    }

    @Test
    fun `skips synthetic and lambda class files`() {
        val results = SymbolScanner.scan(listOf(testProjectClasses)).data

        // No symbols should come from lambda classes
        assertTrue(results.none { "\$" in it.className.value && it.className.value.last().isDigit() },
            "Should skip lambda/anonymous classes")
    }

    @Test
    fun `finds known symbols from test-project`() {
        val results = SymbolScanner.scan(listOf(testProjectClasses)).data
        val symbolsByClass = results.groupBy { it.className.value }

        assertTrue("resetPassword" in (symbolsByClass["com.example.services.UserService"]?.map { it.symbolName } ?: emptyList()),
            "Should find resetPassword in UserService")
    }
}
