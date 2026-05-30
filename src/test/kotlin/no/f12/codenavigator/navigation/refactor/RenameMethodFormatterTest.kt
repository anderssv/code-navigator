package no.f12.codenavigator.navigation.refactor

import no.f12.codenavigator.config.OutputFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RenameMethodFormatterTest {

    private val singleChange = RenameMethodResult(
        changes = listOf(
            RenameChange(
                filePath = "/project/src/main/kotlin/com/example/services/AuditService.kt",
                before = """
                    package com.example.services

                    class AuditService {
                        fun formatAuditEntry(name: String, email: String): String =
                            "audit: ${'$'}name <${'$'}email>"

                        fun auditUser(name: String, email: String): String =
                            formatAuditEntry(name, email)
                    }
                """.trimIndent(),
                after = """
                    package com.example.services

                    class AuditService {
                        fun buildAuditLine(name: String, email: String): String =
                            "audit: ${'$'}name <${'$'}email>"

                        fun auditUser(name: String, email: String): String =
                            buildAuditLine(name, email)
                    }
                """.trimIndent(),
            ),
        ),
    )

    private val config = RenameMethodConfig(
        className = "com.example.services.AuditService",
        methodName = "formatAuditEntry",
        newName = "buildAuditLine",
        preview = false,
        format = OutputFormat.TEXT,
    )

    // [TEST] TEXT format shows file path and diff lines
    // [TEST] TEXT format shows applied header when preview is false
    // [TEST] TEXT format shows preview header when preview is set
    // [TEST] TEXT format shows file count
    // [TEST] JSON format returns object with changes array
    // [TEST] LLM format is compact one-line-per-file
    // [TEST] Empty result produces no-changes message
    // [TEST] TEXT format includes compile recommendation when changes applied
    // [TEST] TEXT format does not include compile recommendation in preview mode
    // [TEST] JSON format includes recommendation field when changes applied

    @Test
    fun `TEXT format shows file path and diff lines`() {
        val output = RenameMethodFormatter.format(singleChange, config)

        assertTrue(output.contains("AuditService.kt"), "Should contain file name")
        assertTrue(output.contains("-"), "Should contain removed lines marker")
        assertTrue(output.contains("+"), "Should contain added lines marker")
        assertTrue(output.contains("formatAuditEntry"), "Should contain old method name")
        assertTrue(output.contains("buildAuditLine"), "Should contain new method name")
    }

    @Test
    fun `TEXT format shows applied header when preview is false`() {
        val output = RenameMethodFormatter.format(singleChange, config)

        assertTrue(output.contains("Applied"), "Should indicate applied mode")
    }

    @Test
    fun `TEXT format shows preview header when preview is set`() {
        val previewConfig = config.copy(preview = true)
        val output = RenameMethodFormatter.format(singleChange, previewConfig)

        assertTrue(output.contains("Preview"), "Should indicate preview mode")
    }

    @Test
    fun `TEXT format shows file count`() {
        val output = RenameMethodFormatter.format(singleChange, config)

        assertTrue(output.contains("1 file"), "Should show file count")
    }

    @Test
    fun `JSON format returns object with changes array`() {
        val jsonConfig = config.copy(format = OutputFormat.JSON)
        val output = RenameMethodFormatter.format(singleChange, jsonConfig)

        assertTrue(output.startsWith("{"), "JSON should start with object. Got:\n$output")
        assertTrue(output.contains("\"changes\""), "Should contain changes key")
        assertTrue(output.contains("\"filePath\""), "Should contain filePath")
        assertTrue(output.contains("\"preview\""), "Should contain preview flag")
    }

    @Test
    fun `LLM format is compact one-line-per-file`() {
        val llmConfig = config.copy(format = OutputFormat.LLM)
        val output = RenameMethodFormatter.format(singleChange, llmConfig)

        assertTrue(output.contains("AuditService.kt"), "Should contain file name")
        assertTrue(output.contains("formatAuditEntry -> buildAuditLine"), "Should show rename")
    }

    @Test
    fun `empty result produces no-changes message`() {
        val emptyResult = RenameMethodResult(emptyList())
        val output = RenameMethodFormatter.format(emptyResult, config)

        assertTrue(output.contains("No changes"), "Should indicate no changes")
    }

    @Test
    fun `TEXT format includes compile recommendation when changes applied`() {
        val output = RenameMethodFormatter.format(singleChange, config)

        assertTrue(output.contains("Compile"), "Should recommend compiling after rename")
    }

    @Test
    fun `TEXT format does not include compile recommendation in preview mode`() {
        val previewConfig = config.copy(preview = true)
        val output = RenameMethodFormatter.format(singleChange, previewConfig)

        assertTrue(!output.contains("Compile"), "Preview should not recommend compiling")
    }

    @Test
    fun `JSON format includes recommendation field when changes applied`() {
        val jsonConfig = config.copy(format = OutputFormat.JSON)
        val output = RenameMethodFormatter.format(singleChange, jsonConfig)

        assertTrue(output.contains("\"recommendation\""), "JSON should contain recommendation key")
        assertTrue(output.contains("Compile"), "Recommendation should mention compiling")
    }

    @Test
    fun `no recommendation in empty result`() {
        val emptyResult = RenameMethodResult(emptyList())
        val output = RenameMethodFormatter.format(emptyResult, config)

        assertTrue(!output.contains("Compile"), "Empty result should not have recommendation")
    }

    @Test
    fun `DIFF format produces raw unified diff with no header`() {
        val diffConfig = config.copy(format = OutputFormat.DIFF)
        val output = RenameMethodFormatter.format(singleChange, diffConfig)

        assertTrue(output.startsWith("--- a/"), "Should start with unified diff header. Got:\n$output")
        assertTrue(output.contains("+++ b/"), "Should contain +++ header")
        assertTrue(output.contains("@@ "), "Should contain hunk header")
        assertTrue(output.contains("-    fun formatAuditEntry"), "Should contain removed line")
        assertTrue(output.contains("+    fun buildAuditLine"), "Should contain added line")
        assertTrue(!output.contains("Applied"), "Should not contain text header")
        assertTrue(!output.contains("Preview"), "Should not contain preview header")
        assertTrue(!output.contains("Compile"), "Should not contain recommendation")
    }

    @Test
    fun `DIFF format returns no-changes message for empty result`() {
        val diffConfig = config.copy(format = OutputFormat.DIFF)
        val emptyResult = RenameMethodResult(emptyList())
        val output = RenameMethodFormatter.format(emptyResult, diffConfig)

        assertEquals("No changes needed.", output)
    }
}
