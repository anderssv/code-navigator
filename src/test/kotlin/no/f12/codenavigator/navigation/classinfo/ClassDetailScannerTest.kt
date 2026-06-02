package no.f12.codenavigator.navigation.classinfo

import no.f12.codenavigator.navigation.classinfo.ClassDetailScanner
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ClassDetailScannerTest {

    private val testProjectClasses = File("test-project/build/classes/kotlin/main")

    @Test
    fun `returns matching classes with details`() {
        val results = ClassDetailScanner.scan(listOf(testProjectClasses), "Service").data

        assertTrue(results.size >= 3, "Should find multiple Service classes, got: ${results.size}")
        val classNames = results.map { it.className.value }
        assertTrue(classNames.any { "UserService" in it })
    }

    @Test
    fun `filters by regex pattern`() {
        val results = ClassDetailScanner.scan(listOf(testProjectClasses), "UserFormatter").data

        assertEquals(1, results.size)
        assertEquals("com.example.domain.UserFormatter", results.single().className.value)
    }

    @Test
    fun `pattern matching is case insensitive`() {
        val results = ClassDetailScanner.scan(listOf(testProjectClasses), "userformatter").data

        assertEquals(1, results.size)
    }

    @Test
    fun `returns empty list when no classes match pattern`() {
        val results = ClassDetailScanner.scan(listOf(testProjectClasses), "NonExistentXyz123").data

        assertTrue(results.isEmpty())
    }

    @Test
    fun `returns empty list for empty directory`() {
        val emptyDir = File("test-project/build/classes/kotlin/main/com/example/nonexistent")

        val results = ClassDetailScanner.scan(listOf(emptyDir), ".*").data

        assertTrue(results.isEmpty())
    }

    @Test
    fun `handles non-existent directory gracefully`() {
        val nonExistent = File("test-project/build/classes/does-not-exist")

        val results = ClassDetailScanner.scan(listOf(nonExistent), ".*").data

        assertTrue(results.isEmpty())
    }

    @Test
    fun `results are sorted by class name`() {
        val results = ClassDetailScanner.scan(listOf(testProjectClasses), ".*").data

        val names = results.map { it.className.value }
        assertEquals(names.sorted(), names)
    }

    @Test
    fun `skips synthetic and anonymous classes`() {
        val results = ClassDetailScanner.scan(listOf(testProjectClasses), "UserRoute").data

        // Should find UserRoute but not UserRoute$handleReset$1
        val names = results.map { it.className.value }
        assertTrue(names.none { it.matches(Regex(".*\\$\\d+$")) },
            "Lambda classes should be skipped: $names")
    }

    @Test
    fun `populates source file in results`() {
        val result = ClassDetailScanner.scan(listOf(testProjectClasses), "UserFormatter").data.single()

        assertEquals("UserFormatter.kt", result.sourceFile)
    }
}
