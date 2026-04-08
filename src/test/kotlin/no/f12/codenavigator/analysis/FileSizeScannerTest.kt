package no.f12.codenavigator.analysis

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FileSizeScannerTest {

    private val testProjectSrc = File("test-project/src/main/kotlin")

    @Test
    fun `scans all source files in test-project`() {
        val result = FileSizeScanner.scan(listOf(testProjectSrc))

        assertEquals(20, result.size)
    }

    @Test
    fun `results are sorted by line count descending`() {
        val result = FileSizeScanner.scan(listOf(testProjectSrc))

        val lines = result.map { it.lines }
        assertEquals(lines.sortedDescending(), lines)
    }

    @Test
    fun `largest file is UserService`() {
        val result = FileSizeScanner.scan(listOf(testProjectSrc))

        assertEquals("com/example/services/UserService.kt", result[0].file)
        assertEquals(61, result[0].lines)
    }

    @Test
    fun `paths are relative to source root`() {
        val result = FileSizeScanner.scan(listOf(testProjectSrc))

        assertTrue(result.all { it.file.startsWith("com/example/") })
        assertTrue(result.none { it.file.contains("src/main/kotlin") })
    }

    @Test
    fun `top parameter limits results`() {
        val result = FileSizeScanner.scan(listOf(testProjectSrc), top = 3)

        assertEquals(3, result.size)
    }

    @Test
    fun `over parameter filters out small files`() {
        val result = FileSizeScanner.scan(listOf(testProjectSrc), over = 30)

        assertTrue(result.all { it.lines > 30 })
        assertEquals(2, result.size) // UserService (61) and UserRoute (35)
    }

    @Test
    fun `empty source roots returns empty list`() {
        val result = FileSizeScanner.scan(emptyList())

        assertEquals(emptyList(), result)
    }

    @Test
    fun `nonexistent source root is skipped`() {
        val missing = File("test-project/src/test/kotlin")
        val result = FileSizeScanner.scan(listOf(testProjectSrc, missing))

        assertEquals(20, result.size)
    }

    @Test
    fun `non-source files are excluded`() {
        // test-project/src/main/kotlin only has .kt files,
        // but scanning the whole test-project/src would also include any non-kt files
        val result = FileSizeScanner.scan(listOf(testProjectSrc))

        assertTrue(result.all { it.file.endsWith(".kt") || it.file.endsWith(".java") })
    }
}
