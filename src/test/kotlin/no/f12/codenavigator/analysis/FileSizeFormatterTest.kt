package no.f12.codenavigator.analysis

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FileSizeFormatterTest {

    // [TEST] Empty list returns "No source files found."
    @Test
    fun `formats empty list`() {
        val result = FileSizeFormatter.format(emptyList())

        assertEquals("No source files found.", result)
    }

    // [TEST] Single file formats as table with File and Lines columns
    @Test
    fun `formats single file as table`() {
        val entries = listOf(FileSizeEntry("services/UserService.kt", 61))

        val result = FileSizeFormatter.format(entries)

        val lines = result.lines()
        assertEquals(2, lines.size)
        assertTrue(lines[0].contains("File"))
        assertTrue(lines[0].contains("Lines"))
        assertTrue(lines[1].contains("services/UserService.kt"))
        assertTrue(lines[1].contains("61"))
    }

    // [TEST] Multiple files format with aligned columns
    @Test
    fun `formats multiple files with aligned columns`() {
        val entries = listOf(
            FileSizeEntry("services/UserService.kt", 61),
            FileSizeEntry("routes/UserRoute.kt", 35),
            FileSizeEntry("domain/Domain.kt", 22),
        )

        val result = FileSizeFormatter.format(entries)

        val lines = result.lines()
        assertEquals(4, lines.size)
        assertTrue(lines[1].contains("services/UserService.kt"))
        assertTrue(lines[2].contains("routes/UserRoute.kt"))
        assertTrue(lines[3].contains("domain/Domain.kt"))
    }

    // [TEST] Large file over threshold gets terse recommendation
    @Test
    fun `large file gets recommendation`() {
        val entries = listOf(
            FileSizeEntry("services/BigService.kt", 300),
            FileSizeEntry("services/MediumService.kt", 50),
            FileSizeEntry("services/SmallService.kt", 30),
            FileSizeEntry("services/TinyService.kt", 20),
            FileSizeEntry("services/MiniService.kt", 15),
        )

        val result = FileSizeFormatter.format(entries)

        assertTrue(result.contains("Consider splitting"), "Large file should get recommendation, got:\n$result")
    }

    // [TEST] Small files below threshold get no recommendation
    @Test
    fun `uniform small files get no recommendation`() {
        val entries = listOf(
            FileSizeEntry("domain/A.kt", 30),
            FileSizeEntry("domain/B.kt", 25),
            FileSizeEntry("domain/C.kt", 20),
            FileSizeEntry("domain/D.kt", 15),
            FileSizeEntry("domain/E.kt", 10),
        )

        val result = FileSizeFormatter.format(entries)

        assertFalse(result.contains("Consider splitting"), "Uniform small files should not be flagged")
    }
}
