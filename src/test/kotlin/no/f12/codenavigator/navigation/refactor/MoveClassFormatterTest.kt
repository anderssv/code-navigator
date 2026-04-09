package no.f12.codenavigator.navigation.refactor

import no.f12.codenavigator.config.OutputFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MoveClassFormatterTest {

    private val singleChange = MoveClassResult(
        changes = listOf(
            RenameChange(
                filePath = "/src/main/kotlin/com/example/services/UserService.kt",
                before = "package com.example.services\n\nclass UserService",
                after = "package com.example.domain\n\nclass UserService",
            ),
        ),
        movedFilePath = "/src/main/kotlin/com/example/services/UserService.kt",
        newFilePath = "/src/main/kotlin/com/example/domain/UserService.kt",
    )

    private val multipleChanges = MoveClassResult(
        changes = listOf(
            RenameChange(
                filePath = "/src/main/kotlin/com/example/services/UserService.kt",
                before = "package com.example.services\n\nclass UserService",
                after = "package com.example.domain\n\nclass UserService",
            ),
            RenameChange(
                filePath = "/src/main/kotlin/com/example/api/Controller.kt",
                before = "import com.example.services.UserService\n\nclass Controller",
                after = "import com.example.domain.UserService\n\nclass Controller",
            ),
        ),
        movedFilePath = "/src/main/kotlin/com/example/services/UserService.kt",
        newFilePath = "/src/main/kotlin/com/example/domain/UserService.kt",
    )

    private val previewConfig = MoveClassConfig(
        className = "com.example.services.UserService",
        newPackage = "com.example.domain",
        newName = null,
        preview = true,
        format = OutputFormat.TEXT,
    )

    private val appliedConfig = previewConfig.copy(preview = false)

    @Test
    fun `TEXT format shows preview header with file count`() {
        val output = MoveClassFormatter.format(singleChange, previewConfig)

        assertTrue(output.contains("Preview:"), "Should show Preview header. Output:\n$output")
        assertTrue(output.contains("com.example.services.UserService"), "Should mention class name. Output:\n$output")
        assertTrue(output.contains("com.example.domain"), "Should mention target package. Output:\n$output")
        assertTrue(output.contains("1 file"), "Should show file count. Output:\n$output")
    }

    @Test
    fun `TEXT format shows Applied header when not preview`() {
        val output = MoveClassFormatter.format(singleChange, appliedConfig)

        assertTrue(output.contains("Applied:"), "Should show Applied header. Output:\n$output")
        assertTrue(output.contains("Compile"), "Should recommend compilation. Output:\n$output")
    }

    @Test
    fun `TEXT format shows diff for each changed file`() {
        val output = MoveClassFormatter.format(multipleChanges, previewConfig)

        assertTrue(output.contains("UserService.kt"), "Should show file path. Output:\n$output")
        assertTrue(output.contains("Controller.kt"), "Should show second file. Output:\n$output")
        assertTrue(output.contains("- package com.example.services"), "Should show removed package. Output:\n$output")
        assertTrue(output.contains("+ package com.example.domain"), "Should show new package. Output:\n$output")
    }

    @Test
    fun `TEXT format returns no changes message for empty result`() {
        val output = MoveClassFormatter.format(MoveClassResult(emptyList()), previewConfig)

        assertEquals("No changes needed.", output)
    }

    @Test
    fun `JSON format includes structured data`() {
        val output = MoveClassFormatter.format(singleChange, previewConfig.copy(format = OutputFormat.JSON))

        assertTrue(output.contains("\"preview\":true"), "Should have preview flag. Output:\n$output")
        assertTrue(output.contains("\"className\":\"com.example.services.UserService\""), "Should have class name. Output:\n$output")
        assertTrue(output.contains("\"newPackage\":\"com.example.domain\""), "Should have new package. Output:\n$output")
        assertTrue(output.contains("\"changes\":["), "Should have changes array. Output:\n$output")
    }

    @Test
    fun `JSON format includes recommendation when not preview`() {
        val output = MoveClassFormatter.format(singleChange, appliedConfig.copy(format = OutputFormat.JSON))

        assertTrue(output.contains("\"recommendation\":"), "Should have recommendation. Output:\n$output")
    }

    @Test
    fun `LLM format is compact`() {
        val output = MoveClassFormatter.format(multipleChanges, previewConfig.copy(format = OutputFormat.LLM))

        assertTrue(output.contains("move-class"), "Should contain move-class. Output:\n$output")
        assertTrue(output.contains("UserService"), "Should mention class. Output:\n$output")
        assertTrue(output.contains("preview"), "Should mention mode. Output:\n$output")
        assertTrue(output.contains("lines="), "Should show line counts. Output:\n$output")
    }

    @Test
    fun `LLM format returns no changes message for empty result`() {
        val output = MoveClassFormatter.format(MoveClassResult(emptyList()), previewConfig.copy(format = OutputFormat.LLM))

        assertEquals("No changes needed.", output)
    }

    @Test
    fun `TEXT format shows multiple files count`() {
        val output = MoveClassFormatter.format(multipleChanges, previewConfig)

        assertTrue(output.contains("2 files"), "Should show plural file count. Output:\n$output")
    }

    // [TEST] TEXT format shows rename header when newName is set
    @Test
    fun `TEXT format shows rename header when newName is set`() {
        val renameConfig = previewConfig.copy(
            newPackage = "com.example.services",
            newName = "AccountService",
        )
        val output = MoveClassFormatter.format(singleChange, renameConfig)

        assertTrue(output.contains("rename"), "Should indicate rename. Output:\n$output")
        assertTrue(output.contains("AccountService"), "Should mention new name. Output:\n$output")
    }

    // [TEST] JSON format includes newName when set
    @Test
    fun `JSON format includes newName when set`() {
        val renameConfig = previewConfig.copy(
            newPackage = "com.example.services",
            newName = "AccountService",
            format = OutputFormat.JSON,
        )
        val output = MoveClassFormatter.format(singleChange, renameConfig)

        assertTrue(output.contains("\"newName\":\"AccountService\""), "Should include newName. Output:\n$output")
    }

    // [TEST] LLM format shows rename info when newName is set
    @Test
    fun `LLM format shows rename info when newName is set`() {
        val renameConfig = previewConfig.copy(
            newPackage = "com.example.services",
            newName = "AccountService",
            format = OutputFormat.LLM,
        )
        val output = MoveClassFormatter.format(singleChange, renameConfig)

        assertTrue(output.contains("AccountService"), "Should mention new name. Output:\n$output")
    }
}
