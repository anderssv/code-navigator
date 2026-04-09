package no.f12.codenavigator.navigation.refactor

import no.f12.codenavigator.config.OutputFormat
import kotlin.test.Test
import kotlin.test.assertTrue

class RenamePropertyFormatterTest {

    private val singleChange = RenamePropertyResult(
        changes = listOf(
            RenameChange(
                filePath = "/project/src/main/kotlin/com/example/data/UserProfile.kt",
                before = """
                    package com.example.data

                    data class UserProfile(val fullName: String, val email: String)

                    class ProfileService {
                        fun createProfile(name: String, email: String): UserProfile =
                            UserProfile(fullName = name, email = email)

                        fun displayName(profile: UserProfile): String =
                            profile.fullName
                    }
                """.trimIndent(),
                after = """
                    package com.example.data

                    data class UserProfile(val displayName: String, val email: String)

                    class ProfileService {
                        fun createProfile(name: String, email: String): UserProfile =
                            UserProfile(displayName = name, email = email)

                        fun showName(profile: UserProfile): String =
                            profile.displayName
                    }
                """.trimIndent(),
            ),
        ),
    )

    private val config = RenamePropertyConfig(
        className = "com.example.data.UserProfile",
        propertyName = "fullName",
        newName = "displayName",
        preview = false,
        format = OutputFormat.TEXT,
    )

    @Test
    fun `TEXT format shows file path and diff lines`() {
        val output = RenamePropertyFormatter.format(singleChange, config)

        assertTrue(output.contains("UserProfile.kt"), "Should contain file name")
        assertTrue(output.contains("-"), "Should contain removed lines marker")
        assertTrue(output.contains("+"), "Should contain added lines marker")
        assertTrue(output.contains("fullName"), "Should contain old property name")
        assertTrue(output.contains("displayName"), "Should contain new property name")
    }

    @Test
    fun `TEXT format shows applied header when preview is false`() {
        val output = RenamePropertyFormatter.format(singleChange, config)

        assertTrue(output.contains("Applied"), "Should indicate applied mode")
    }

    @Test
    fun `TEXT format shows preview header when preview is set`() {
        val previewConfig = config.copy(preview = true)
        val output = RenamePropertyFormatter.format(singleChange, previewConfig)

        assertTrue(output.contains("Preview"), "Should indicate preview mode")
    }

    @Test
    fun `TEXT format shows file count`() {
        val output = RenamePropertyFormatter.format(singleChange, config)

        assertTrue(output.contains("1 file"), "Should show file count")
    }

    @Test
    fun `JSON format returns object with changes array`() {
        val jsonConfig = config.copy(format = OutputFormat.JSON)
        val output = RenamePropertyFormatter.format(singleChange, jsonConfig)

        assertTrue(output.startsWith("{"), "JSON should start with object. Got:\n$output")
        assertTrue(output.contains("\"changes\""), "Should contain changes key")
        assertTrue(output.contains("\"filePath\""), "Should contain filePath")
        assertTrue(output.contains("\"preview\""), "Should contain preview flag")
    }

    @Test
    fun `LLM format is compact one-line-per-file`() {
        val llmConfig = config.copy(format = OutputFormat.LLM)
        val output = RenamePropertyFormatter.format(singleChange, llmConfig)

        assertTrue(output.contains("UserProfile.kt"), "Should contain file name")
        assertTrue(output.contains("fullName -> displayName"), "Should show rename")
    }

    @Test
    fun `empty result produces no-changes message`() {
        val emptyResult = RenamePropertyResult(emptyList())
        val output = RenamePropertyFormatter.format(emptyResult, config)

        assertTrue(output.contains("No changes"), "Should indicate no changes")
    }

    @Test
    fun `TEXT format includes compile recommendation when changes applied`() {
        val output = RenamePropertyFormatter.format(singleChange, config)

        assertTrue(output.contains("Compile"), "Should recommend compiling after rename")
    }

    @Test
    fun `TEXT format does not include compile recommendation in preview mode`() {
        val previewConfig = config.copy(preview = true)
        val output = RenamePropertyFormatter.format(singleChange, previewConfig)

        assertTrue(!output.contains("Compile"), "Preview should not recommend compiling")
    }

    @Test
    fun `JSON format includes recommendation field when changes applied`() {
        val jsonConfig = config.copy(format = OutputFormat.JSON)
        val output = RenamePropertyFormatter.format(singleChange, jsonConfig)

        assertTrue(output.contains("\"recommendation\""), "JSON should contain recommendation key")
        assertTrue(output.contains("Compile"), "Recommendation should mention compiling")
    }

    @Test
    fun `no recommendation in empty result`() {
        val emptyResult = RenamePropertyResult(emptyList())
        val output = RenamePropertyFormatter.format(emptyResult, config)

        assertTrue(!output.contains("Compile"), "Empty result should not have recommendation")
    }
}
