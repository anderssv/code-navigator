package no.f12.codenavigator.navigation

import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SkippedFileReporterTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `returns summary message with count for multiple skipped files`() {
        val reportFile = tempDir.resolve("report.txt").toFile()
        val skipped = listOf(
            UnsupportedBytecodeVersionException("Cannot read Foo.class: unsupported bytecode version (Java 26)"),
            UnsupportedBytecodeVersionException("Cannot read Bar.class: unsupported bytecode version (Java 26)"),
            UnsupportedBytecodeVersionException("Cannot read Baz.class: unsupported bytecode version (Java 26)"),
        )

        val result = SkippedFileReporter.report(skipped, reportFile)

        assertNotNull(result)
        assertTrue(result.contains("3"), "Should mention count of 3")
    }

    @Test
    fun `returns null when skipped files list is empty`() {
        val reportFile = tempDir.resolve("report.txt").toFile()

        val result = SkippedFileReporter.report(emptyList(), reportFile)

        assertNull(result)
    }

    @Test
    fun `returns summary message with count for one skipped file`() {
        val reportFile = tempDir.resolve("report.txt").toFile()
        val skipped = listOf(UnsupportedBytecodeVersionException("Cannot read Foo.class: unsupported bytecode version (Java 26)"))

        val result = SkippedFileReporter.report(skipped, reportFile)

        assertNotNull(result)
        assertTrue(result.contains("1"), "Should mention count")
        assertTrue(result.contains(reportFile.absolutePath), "Should mention report file path")
    }

    @Test
    fun `writes error messages to report file`() {
        val reportFile = tempDir.resolve("report.txt").toFile()
        val skipped = listOf(
            UnsupportedBytecodeVersionException("Cannot read Foo.class: unsupported bytecode version (Java 26)"),
            UnsupportedBytecodeVersionException("Cannot read Bar.class: unsupported bytecode version (Java 26)"),
        )

        SkippedFileReporter.report(skipped, reportFile)

        val lines = reportFile.readText().lines()
        assertTrue(lines[0].contains("Foo.class"), "First line should mention Foo.class")
        assertTrue(lines[1].contains("Bar.class"), "Second line should mention Bar.class")
    }

    @Test
    fun `creates parent directories for report file`() {
        val reportFile = tempDir.resolve("nested/deep/report.txt").toFile()
        val skipped = listOf(UnsupportedBytecodeVersionException("Cannot read Foo.class: unsupported bytecode version (Java 26)"))

        SkippedFileReporter.report(skipped, reportFile)

        assertTrue(reportFile.exists(), "Report file should exist in nested directory")
    }

    @Test
    fun `does not create report file when list is empty`() {
        val reportFile = tempDir.resolve("report.txt").toFile()

        SkippedFileReporter.report(emptyList(), reportFile)

        assertTrue(!reportFile.exists(), "Report file should not be created when list is empty")
    }
}
