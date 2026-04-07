package no.f12.codenavigator.navigation.refactor

import no.f12.codenavigator.config.OutputFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RenameParamFormatterTest {

    private val singleChange = RenameResult(
        changes = listOf(
            RenameChange(
                filePath = "/project/src/main/kotlin/com/example/services/AuditService.kt",
                before = """
                    package com.example.services

                    class AuditService {
                        fun formatAuditEntry(name: String, email: String): String =
                            "audit: ${'$'}name <${'$'}email>"
                    }
                """.trimIndent(),
                after = """
                    package com.example.services

                    class AuditService {
                        fun formatAuditEntry(userName: String, email: String): String =
                            "audit: ${'$'}userName <${'$'}email>"
                    }
                """.trimIndent(),
            ),
        ),
    )

    private val resultWithCascade = RenameResult(
        changes = singleChange.changes,
        cascadeCandidates = listOf(
            CascadeCandidate(
                className = "com.example.services.AuditService",
                methodName = "buildLine",
                paramName = "name",
            ),
        ),
    )

    private val config = RenameParamConfig(
        className = "com.example.services.AuditService",
        methodName = "formatAuditEntry",
        paramName = "name",
        newName = "userName",
        preview = false,
        format = OutputFormat.TEXT,
    )

    // [TEST] TEXT format shows file path and diff lines
    @Test
    fun `TEXT format shows file path and diff lines`() {
        val output = RenameParamFormatter.format(singleChange, config)

        assertTrue(output.contains("AuditService.kt"), "Should contain file name")
        assertTrue(output.contains("-"), "Should contain removed lines marker")
        assertTrue(output.contains("+"), "Should contain added lines marker")
        assertTrue(output.contains("name"), "Should contain old param name")
        assertTrue(output.contains("userName"), "Should contain new param name")
    }

    // [TEST] TEXT format shows applied header when preview is false
    @Test
    fun `TEXT format shows applied header when preview is false`() {
        val output = RenameParamFormatter.format(singleChange, config)

        assertTrue(output.contains("Applied"), "Should indicate applied mode")
    }

    // [TEST] TEXT format shows preview header when preview is set
    @Test
    fun `TEXT format shows preview header when preview is set`() {
        val previewConfig = config.copy(preview = true)
        val output = RenameParamFormatter.format(singleChange, previewConfig)

        assertTrue(output.contains("Preview"), "Should indicate preview mode")
    }

    // [TEST] TEXT format shows file count
    @Test
    fun `TEXT format shows file count`() {
        val output = RenameParamFormatter.format(singleChange, config)

        assertTrue(output.contains("1 file"), "Should show file count")
    }

    // [TEST] JSON format returns object with changes array
    @Test
    fun `JSON format returns object with changes array`() {
        val jsonConfig = config.copy(format = OutputFormat.JSON)
        val output = RenameParamFormatter.format(singleChange, jsonConfig)

        assertTrue(output.startsWith("{"), "JSON should start with object. Got:\n$output")
        assertTrue(output.contains("\"changes\""), "Should contain changes key")
        assertTrue(output.contains("\"filePath\""), "Should contain filePath")
        assertTrue(output.contains("\"preview\""), "Should contain preview flag")
    }

    // [TEST] LLM format is compact one-line-per-file
    @Test
    fun `LLM format is compact one-line-per-file`() {
        val llmConfig = config.copy(format = OutputFormat.LLM)
        val output = RenameParamFormatter.format(singleChange, llmConfig)

        assertTrue(output.contains("AuditService.kt"), "Should contain file name")
        assertTrue(output.contains("name -> userName"), "Should show rename")
    }

    // [TEST] Empty result produces no-changes message
    @Test
    fun `empty result produces no-changes message`() {
        val emptyResult = RenameResult(emptyList())
        val output = RenameParamFormatter.format(emptyResult, config)

        assertTrue(output.contains("No changes"), "Should indicate no changes")
    }

    // [TEST] TEXT format includes compile recommendation when changes applied
    @Test
    fun `TEXT format includes compile recommendation when changes applied`() {
        val output = RenameParamFormatter.format(singleChange, config)

        assertTrue(output.contains("Compile"), "Should recommend compiling after rename")
    }

    // [TEST] TEXT format does not include compile recommendation in preview mode
    @Test
    fun `TEXT format does not include compile recommendation in preview mode`() {
        val previewConfig = config.copy(preview = true)
        val output = RenameParamFormatter.format(singleChange, previewConfig)

        assertTrue(!output.contains("Compile"), "Preview should not recommend compiling")
    }

    // [TEST] JSON format includes recommendation field when changes applied
    @Test
    fun `JSON format includes recommendation field when changes applied`() {
        val jsonConfig = config.copy(format = OutputFormat.JSON)
        val output = RenameParamFormatter.format(singleChange, jsonConfig)

        assertTrue(output.contains("\"recommendation\""), "JSON should contain recommendation key")
        assertTrue(output.contains("Compile"), "Recommendation should mention compiling")
    }

    // [TEST] LLM format includes compile recommendation when changes applied
    @Test
    fun `LLM format includes compile recommendation when changes applied`() {
        val llmConfig = config.copy(format = OutputFormat.LLM)
        val output = RenameParamFormatter.format(singleChange, llmConfig)

        assertTrue(output.contains("Compile"), "LLM should recommend compiling after rename")
    }

    // [TEST] TEXT format shows cascade candidates when present
    @Test
    fun `TEXT format shows cascade candidates when present`() {
        val output = RenameParamFormatter.format(resultWithCascade, config)

        assertTrue(output.contains("buildLine"), "Should mention cascade method name. Got:\n$output")
        assertTrue(output.contains("name"), "Should mention cascade param name. Got:\n$output")
    }

    // [TEST] TEXT format does not show cascade section when no candidates
    @Test
    fun `TEXT format does not show cascade section when no candidates`() {
        val output = RenameParamFormatter.format(singleChange, config)

        assertTrue(!output.contains("cascade", ignoreCase = true), "Should not mention cascade when no candidates. Got:\n$output")
    }

    // [TEST] JSON format includes cascadeCandidates array when present
    @Test
    fun `JSON format includes cascadeCandidates array when present`() {
        val jsonConfig = config.copy(format = OutputFormat.JSON)
        val output = RenameParamFormatter.format(resultWithCascade, jsonConfig)

        assertTrue(output.contains("\"cascadeCandidates\""), "Should contain cascadeCandidates key. Got:\n$output")
        assertTrue(output.contains("\"buildLine\""), "Should contain method name. Got:\n$output")
        assertTrue(output.contains("\"name\""), "Should contain param name. Got:\n$output")
    }

    // [TEST] JSON format omits cascadeCandidates when empty
    @Test
    fun `JSON format omits cascadeCandidates when empty`() {
        val jsonConfig = config.copy(format = OutputFormat.JSON)
        val output = RenameParamFormatter.format(singleChange, jsonConfig)

        assertTrue(!output.contains("cascadeCandidates"), "Should not contain cascadeCandidates when empty. Got:\n$output")
    }

    // [TEST] LLM format shows cascade candidates when present
    @Test
    fun `LLM format shows cascade candidates when present`() {
        val llmConfig = config.copy(format = OutputFormat.LLM)
        val output = RenameParamFormatter.format(resultWithCascade, llmConfig)

        assertTrue(output.contains("buildLine"), "Should mention cascade method. Got:\n$output")
        assertTrue(output.contains("name"), "Should mention cascade param. Got:\n$output")
    }

    // [TEST] No recommendation in empty result
    @Test
    fun `no recommendation in empty result`() {
        val emptyResult = RenameResult(emptyList())
        val output = RenameParamFormatter.format(emptyResult, config)

        assertTrue(!output.contains("Compile"), "Empty result should not have recommendation")
    }
}
