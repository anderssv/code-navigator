package no.f12.codenavigator.navigation.classinfo

import no.f12.codenavigator.navigation.classinfo.ClassScanner
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ClassScannerTest {

    private val testProjectClasses = File("test-project/build/classes/kotlin/main")

    @Test
    fun `scans directory and finds class files`() {
        val results = ClassScanner.scan(listOf(testProjectClasses)).data

        assertTrue(results.size > 10, "Should find many classes in test-project, got: ${results.size}")
    }

    @Test
    fun `returns empty list for empty directory`() {
        val emptyDir = File("test-project/build/classes/kotlin/main/com/example/nonexistent")

        val results = ClassScanner.scan(listOf(emptyDir)).data

        assertTrue(results.isEmpty())
    }

    @Test
    fun `filters out anonymous and synthetic classes`() {
        val results = ClassScanner.scan(listOf(testProjectClasses)).data

        // UserRoute has lambda classes (UserRoute$handleReset$1) — they should be filtered
        val anonymousClasses = results.filter { "\$" in it.className.value && it.className.value.last().isDigit() }
        assertTrue(anonymousClasses.isEmpty(), "Anonymous/lambda classes should be filtered: ${anonymousClasses.map { it.className.value }}")
    }

    @Test
    fun `keeps named inner classes`() {
        val results = ClassScanner.scan(listOf(testProjectClasses)).data

        val classNames = results.map { it.className.value }.toSet()
        // Companion objects and sealed subclasses are named inner classes
        assertTrue("com.example.infra.EventSender\$Companion" in classNames,
            "Should keep named inner classes like Companion")
        assertTrue("com.example.domain.UserError\$NotFound" in classNames,
            "Should keep sealed subclasses")
    }

    @Test
    fun `results are sorted alphabetically by class name`() {
        val results = ClassScanner.scan(listOf(testProjectClasses)).data

        val names = results.map { it.className.value }
        assertEquals(names.sorted(), names)
    }

    @Test
    fun `handles non-existent directory gracefully`() {
        val nonExistent = File("test-project/build/classes/does-not-exist")

        val results = ClassScanner.scan(listOf(nonExistent)).data

        assertTrue(results.isEmpty())
    }

    @Test
    fun `finds specific known classes`() {
        val results = ClassScanner.scan(listOf(testProjectClasses)).data
        val classNames = results.map { it.className.value }.toSet()

        assertTrue("com.example.domain.User" in classNames)
        assertTrue("com.example.infra.InMemoryUserRepository" in classNames)
        assertTrue("com.example.services.UserService" in classNames)
    }
}
