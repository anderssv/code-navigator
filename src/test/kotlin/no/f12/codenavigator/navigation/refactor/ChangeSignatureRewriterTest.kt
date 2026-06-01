package no.f12.codenavigator.navigation.refactor

import kotlin.test.Test
import kotlin.test.assertTrue
import java.io.File
import java.nio.file.Files

class ChangeSignatureRewriterTest {

    private val testProjectSrc = File("test-project/src/main/kotlin")
    private val testProjectClasses = File("test-project/build/classes/kotlin/main")

    // [TEST] Adds a parameter to method declaration
    // [TEST] Adds default value for new parameter at call sites
    // [TEST] Removes a parameter from method declaration
    // [TEST] Removes corresponding argument from call sites
    // [TEST] Reorders parameters in method declaration
    // [TEST] Reorders positional arguments at call sites
    // [TEST] Leaves named arguments unchanged when reordering
    // [TEST] Refuses when no defaults provided for new params
    // [TEST] Preview mode does not write to disk
    // [TEST] Non-existent method returns not-found

    @Test
    fun `adds a parameter to method declaration`() {
        val sourceDir = copySourcesToTemp("changesig-add", "com/example/variants/changesig")

        val result = ChangeSignatureRewriter.change(
            sourceRoots = listOf(sourceDir),
            classDirectories = listOf(testProjectClasses),
            className = "com.example.variants.changesig.UserService",
            methodName = "findUsers",
            newParams = "limit: Int, offset: Int, query: String",
            defaults = mapOf("query" to "\"\""),
            preview = false,
        )

        assertTrue(result.changes.isNotEmpty(), "Should have changes. Result: $result")
        val declChange = result.changes.first { it.filePath.endsWith("UserService.kt") }
        assertTrue(declChange.after.contains("query: String"), "Declaration should contain new param. Content:\n${declChange.after}")
        assertTrue(declChange.after.contains("limit: Int, offset: Int, query: String"), "All params in order. Content:\n${declChange.after}")
    }

    @Test
    fun `adds default value for new parameter at call sites`() {
        val sourceDir = copySourcesToTemp("changesig-add-callsite", "com/example/variants/changesig")

        val result = ChangeSignatureRewriter.change(
            sourceRoots = listOf(sourceDir),
            classDirectories = listOf(testProjectClasses),
            className = "com.example.variants.changesig.UserService",
            methodName = "findUsers",
            newParams = "limit: Int, offset: Int, query: String",
            defaults = mapOf("query" to "\"\""),
            preview = false,
        )

        val declChange = result.changes.first { it.filePath.endsWith("UserService.kt") }
        assertTrue(declChange.after.contains("findUsers(10, 0, \"\")"), "Call site should include default for new param. Content:\n${declChange.after}")
        assertTrue(declChange.after.contains("findUsers(20, 0, \"\")"), "Second call site should include default. Content:\n${declChange.after}")
    }

    @Test
    fun `removes a parameter from declaration and call sites`() {
        val sourceDir = copySourcesToTemp("changesig-remove", "com/example/variants/changesig")

        val result = ChangeSignatureRewriter.change(
            sourceRoots = listOf(sourceDir),
            classDirectories = listOf(testProjectClasses),
            className = "com.example.variants.changesig.UserService",
            methodName = "findUsers",
            newParams = "limit: Int",
            defaults = emptyMap(),
            preview = false,
        )

        assertTrue(result.changes.isNotEmpty(), "Should have changes")
        val declChange = result.changes.first { it.filePath.endsWith("UserService.kt") }
        assertTrue(declChange.after.contains("fun findUsers(limit: Int)"), "Declaration should only have limit. Content:\n${declChange.after}")
        assertTrue(declChange.after.contains("findUsers(10)"), "Call site should drop removed param. Content:\n${declChange.after}")
        assertTrue(declChange.after.contains("findUsers(20)"), "Second call site too. Content:\n${declChange.after}")
    }

    @Test
    fun `reorders parameters in declaration and positional call sites`() {
        val sourceDir = copySourcesToTemp("changesig-reorder", "com/example/variants/changesig")

        val result = ChangeSignatureRewriter.change(
            sourceRoots = listOf(sourceDir),
            classDirectories = listOf(testProjectClasses),
            className = "com.example.variants.changesig.UserService",
            methodName = "findUsers",
            newParams = "offset: Int, limit: Int",
            defaults = emptyMap(),
            preview = false,
        )

        assertTrue(result.changes.isNotEmpty(), "Should have changes")
        val declChange = result.changes.first { it.filePath.endsWith("UserService.kt") }
        assertTrue(declChange.after.contains("fun findUsers(offset: Int, limit: Int)"), "Declaration reordered. Content:\n${declChange.after}")
        assertTrue(declChange.after.contains("findUsers(0, 10)"), "Call site args reordered. Content:\n${declChange.after}")
        assertTrue(declChange.after.contains("findUsers(0, 20)"), "Second call site reordered. Content:\n${declChange.after}")
    }

    @Test
    fun `refuses when no default provided for new param`() {
        val sourceDir = copySourcesToTemp("changesig-nodefault", "com/example/variants/changesig")

        val result = ChangeSignatureRewriter.change(
            sourceRoots = listOf(sourceDir),
            classDirectories = listOf(testProjectClasses),
            className = "com.example.variants.changesig.UserService",
            methodName = "findUsers",
            newParams = "limit: Int, offset: Int, query: String",
            defaults = emptyMap(),
            preview = false,
        )

        assertTrue(result.changes.isEmpty(), "Should have no changes when default missing")
        assertTrue(result.reason?.contains("No default") == true, "Should explain why. Reason: ${result.reason}")
    }

    @Test
    fun `leaves named arguments unchanged when reordering`() {
        val sourceDir = copySourcesToTemp("changesig-named", "com/example/variants/changesig")

        val result = ChangeSignatureRewriter.change(
            sourceRoots = listOf(sourceDir),
            classDirectories = listOf(testProjectClasses),
            className = "com.example.variants.changesig.NamedArgService",
            methodName = "findUsers",
            newParams = "offset: Int, limit: Int",
            defaults = emptyMap(),
            preview = false,
        )

        assertTrue(result.changes.isNotEmpty(), "Should have changes")
        val change = result.changes.first { it.filePath.endsWith("NamedArgService.kt") }
        // Named args should keep their names, just reordered by name
        assertTrue(change.after.contains("limit = 10"), "Named arg 'limit' preserved. Content:\n${change.after}")
        assertTrue(change.after.contains("offset = 0"), "Named arg 'offset' preserved. Content:\n${change.after}")
    }

    @Test
    fun `returns not-found for non-existent method`() {
        val sourceDir = copySourcesToTemp("changesig-notfound", "com/example/variants/changesig")

        val result = ChangeSignatureRewriter.change(
            sourceRoots = listOf(sourceDir),
            classDirectories = listOf(testProjectClasses),
            className = "com.example.variants.changesig.UserService",
            methodName = "nonExistent",
            newParams = "x: Int",
            defaults = emptyMap(),
            preview = false,
        )

        assertTrue(result.changes.isEmpty(), "No changes for missing method")
        assertTrue(result.reason?.contains("not found") == true, "Should say not found. Reason: ${result.reason}")
    }

    @Test
    fun `preview mode does not write to disk`() {
        val sourceDir = copySourcesToTemp("changesig-preview", "com/example/variants/changesig")
        val file = File(sourceDir, "com/example/variants/changesig/UserService.kt")
        val before = file.readText()

        val result = ChangeSignatureRewriter.change(
            sourceRoots = listOf(sourceDir),
            classDirectories = listOf(testProjectClasses),
            className = "com.example.variants.changesig.UserService",
            methodName = "findUsers",
            newParams = "offset: Int, limit: Int",
            defaults = emptyMap(),
            preview = true,
        )

        assertTrue(result.changes.isNotEmpty(), "Should report changes")
        kotlin.test.assertEquals(before, file.readText(), "File should not be modified on disk")
    }

    @Test
    fun `finds and changes suspend function signature`() {
        val sourceDir = copySourcesToTemp("changesig-suspend", "com/example/variants/changesig")

        val result = ChangeSignatureRewriter.change(
            sourceRoots = listOf(sourceDir),
            classDirectories = listOf(testProjectClasses),
            className = "com.example.variants.changesig.UserService",
            methodName = "fetchRemote",
            newParams = "url: String, timeout: Int, retries: Int",
            defaults = mapOf("retries" to "3"),
            preview = true,
        )

        assertTrue(result.changes.isNotEmpty(), "Should find suspend function. Result: $result")
        val declChange = result.changes.first { it.filePath.endsWith("UserService.kt") }
        assertTrue(declChange.after.contains("retries: Int"), "Declaration should contain new param")
        assertTrue(declChange.after.contains("fetchRemote(\"http://example.com\", 5000, 3)"), "Call site should have default. Content:\n${declChange.after}")
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
