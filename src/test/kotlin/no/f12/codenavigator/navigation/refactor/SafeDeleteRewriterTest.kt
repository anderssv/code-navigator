package no.f12.codenavigator.navigation.refactor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import java.io.File
import java.nio.file.Files

class SafeDeleteRewriterTest {

    private val testProjectSrc = File("test-project/src/main/kotlin")
    private val testProjectClasses = File("test-project/build/classes/kotlin/main")

    @Test
    fun `deletes an unused class from source file`() {
        val sourceDir = copySourcesToTemp("safe-delete-unused-class", "com/example/variants/safedelete")
        val result = SafeDeleteRewriter.delete(
            sourceRoots = listOf(sourceDir),
            classDirectories = listOf(testProjectClasses),
            className = "com.example.variants.safedelete.UnusedService",
            preview = false,
        )

        assertTrue(result.deleted, "Should successfully delete unused class. Result: $result")
        assertTrue(result.changes.isNotEmpty(), "Should have changes")
        val change = result.changes.first()
        assertTrue(!change.after.contains("class UnusedService"), "UnusedService should be removed from source")
    }

    @Test
    fun `refuses to delete a class that has usages`() {
        val result = SafeDeleteRewriter.delete(
            sourceRoots = listOf(testProjectSrc),
            classDirectories = listOf(testProjectClasses),
            className = "com.example.variants.safedelete.UsedService",
            preview = true,
        )

        assertTrue(!result.deleted, "Should refuse to delete a class with usages")
        assertTrue(result.usages.isNotEmpty(), "Should report usages. Result: $result")
        assertTrue(result.reason != null, "Should provide a reason")
    }

    @Test
    fun `deletes an unused method from a class`() {
        val sourceDir = copySourcesToTemp("safe-delete-unused-method", "com/example/variants/safedelete")
        val result = SafeDeleteRewriter.delete(
            sourceRoots = listOf(sourceDir),
            classDirectories = listOf(testProjectClasses),
            className = "com.example.variants.safedelete.UsedService",
            methodName = "unusedMethod",
            preview = false,
        )

        assertTrue(result.deleted, "Should successfully delete unused method. Result: $result")
        assertTrue(result.changes.isNotEmpty(), "Should have changes")
        val change = result.changes.first()
        assertTrue(!change.after.contains("unusedMethod"), "unusedMethod should be removed. Content:\n${change.after}")
        assertTrue(change.after.contains("activeMethod"), "activeMethod should remain. Content:\n${change.after}")
    }

    @Test
    fun `refuses to delete a method that has usages`() {
        val result = SafeDeleteRewriter.delete(
            sourceRoots = listOf(testProjectSrc),
            classDirectories = listOf(testProjectClasses),
            className = "com.example.variants.safedelete.UsedService",
            methodName = "activeMethod",
            preview = true,
        )

        assertTrue(!result.deleted, "Should refuse to delete a method with usages")
        assertTrue(result.usages.isNotEmpty(), "Should report usages. Result: $result")
    }

    @Test
    fun `preview mode does not write to disk`() {
        val sourceDir = copySourcesToTemp("safe-delete-preview", "com/example/variants/safedelete")
        val file = File(sourceDir, "com/example/variants/safedelete/Services.kt")
        val originalContent = file.readText()

        val result = SafeDeleteRewriter.delete(
            sourceRoots = listOf(sourceDir),
            classDirectories = listOf(testProjectClasses),
            className = "com.example.variants.safedelete.UnusedService",
            preview = true,
        )

        assertTrue(result.deleted, "Should report successful deletion")
        assertEquals(originalContent, file.readText(), "File should not be modified in preview mode")
    }

    @Test
    fun `non-existent class returns not-found result`() {
        val result = SafeDeleteRewriter.delete(
            sourceRoots = listOf(testProjectSrc),
            classDirectories = listOf(testProjectClasses),
            className = "com.example.variants.safedelete.DoesNotExist",
            preview = true,
        )

        assertTrue(!result.deleted, "Should not delete non-existent class")
        assertTrue(result.reason?.contains("not found") == true, "Should say not found. Reason: ${result.reason}")
    }

    @Test
    fun `SafeDeleteResult JSON roundtrip`() {
        val result = SafeDeleteRewriter.delete(
            sourceRoots = listOf(testProjectSrc),
            classDirectories = listOf(testProjectClasses),
            className = "com.example.variants.safedelete.UnusedService",
            preview = true,
        )

        val json = result.toJson()
        assertTrue(json.contains("\"deleted\":true"), "JSON should contain deleted:true. JSON: $json")
        assertTrue(json.contains("\"changes\":["), "JSON should contain changes array. JSON: $json")
    }

    private fun copySourcesToTemp(label: String, vararg packages: String): File {
        val tempDir = Files.createTempDirectory("cnav-test-$label").toFile()
        for (pkg in packages) {
            val srcPkg = File(testProjectSrc, pkg)
            val destPkg = File(tempDir, pkg)
            srcPkg.copyRecursively(destPkg)
        }
        return tempDir
    }
}
