package no.f12.codenavigator.navigation.refactor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import java.io.File

class RenameMethodRewriterTest {

    // [TEST] Renames method in declaration and call site compiles
    @Test
    fun `returns change with before and after content`() {
        val tempDir = copyTestSources("rename-method-change-info")
        val sourceDir = File(tempDir, "src/main/kotlin")

        val result = RenameMethodRewriter.rename(
            sourceRoots = listOf(sourceDir),
            className = "com.example.services.AuditService",
            methodName = "formatAuditEntry",
            newName = "buildAuditLine",
        )

        assertTrue(result.changes.isNotEmpty())
        val change = result.changes.first()
        assertTrue(change.before.contains("formatAuditEntry"))
        assertTrue(change.after.contains("buildAuditLine"))
        assertTrue(change.filePath.endsWith("AuditService.kt"))

        tempDir.deleteRecursively()
    }

    @Test
    fun `leaves unrelated files unchanged`() {
        val tempDir = copyTestSources("rename-method-unrelated")
        val sourceDir = File(tempDir, "src/main/kotlin")
        val domainFile = File(sourceDir, "com/example/domain/Domain.kt")
        val domainBefore = domainFile.readText()

        RenameMethodRewriter.rename(
            sourceRoots = listOf(sourceDir),
            className = "com.example.services.AuditService",
            methodName = "formatAuditEntry",
            newName = "buildAuditLine",
        )

        assertEquals(domainBefore, domainFile.readText(), "Domain.kt should be unchanged")

        tempDir.deleteRecursively()
    }

    @Test
    fun `preview mode does not write to disk`() {
        val tempDir = copyTestSources("rename-method-preview")
        val sourceDir = File(tempDir, "src/main/kotlin")
        val auditFile = File(sourceDir, "com/example/services/AuditService.kt")
        val originalContent = auditFile.readText()

        val result = RenameMethodRewriter.rename(
            sourceRoots = listOf(sourceDir),
            className = "com.example.services.AuditService",
            methodName = "formatAuditEntry",
            newName = "buildAuditLine",
            apply = false,
        )

        assertTrue(result.changes.isNotEmpty(), "Should detect changes")
        assertEquals(originalContent, auditFile.readText(), "File should not be modified in preview mode")

        tempDir.deleteRecursively()
    }

    @Test
    fun `renames method called via interface type`() {
        val tempDir = copyTestSources("rename-method-interface")
        val sourceDir = File(tempDir, "src/main/kotlin")

        // Target: rename UserRepository.findById -> lookupById
        // The interface declares findById, and callers use the interface type

        val result = RenameMethodRewriter.rename(
            sourceRoots = listOf(sourceDir),
            className = "com.example.domain.UserRepository",
            methodName = "findById",
            newName = "lookupById",
        )

        assertTrue(result.changes.isNotEmpty(), "Should have changes. Result: $result")

        // Check the interface declaration was renamed
        val domainFile = File(sourceDir, "com/example/domain/Domain.kt")
        val domainContent = domainFile.readText()
        assertTrue(domainContent.contains("fun lookupById("), "Interface method should be renamed. Content:\n$domainContent")
        assertTrue(!domainContent.contains("fun findById("), "Old method name should be gone from interface. Content:\n$domainContent")

        // Check implementation was renamed
        val implFile = File(sourceDir, "com/example/infra/InMemoryUserRepository.kt")
        val implContent = implFile.readText()
        assertTrue(implContent.contains("fun lookupById("), "Implementation should be renamed. Content:\n$implContent")

        // Check callers (AuditService calls repository.findById)
        val auditFile = File(sourceDir, "com/example/services/AuditService.kt")
        val auditContent = auditFile.readText()
        assertTrue(auditContent.contains("lookupById("), "Call site should be renamed. Content:\n$auditContent")
        assertTrue(!auditContent.contains("findById("), "Old method name should be gone from call site. Content:\n$auditContent")

        compileKotlin(tempDir)

        tempDir.deleteRecursively()
    }

    @Test
    fun `renames method at cross-file call site`() {
        val tempDir = copyTestSources("rename-method-cross")
        val sourceDir = File(tempDir, "src/main/kotlin")
        val callerFile = File(sourceDir, "com/example/services/ReportService.kt")
        callerFile.parentFile.mkdirs()
        callerFile.writeText("""
            package com.example.services

            class ReportService(
                private val auditService: AuditService,
            ) {
                fun generateReport(userId: String): String =
                    auditService.formatAuditEntry("test", "test@example.com")
            }
        """.trimIndent() + "\n")

        // Make formatAuditEntry non-private so it can be called from another class
        val auditFile = File(sourceDir, "com/example/services/AuditService.kt")
        auditFile.writeText("""
            package com.example.services

            import com.example.domain.UserRepository

            class AuditService(
                private val repository: UserRepository,
            ) {
                fun auditUser(userId: String): String {
                    val user = repository.findById(userId) ?: return "not found"
                    return formatAuditEntry(user.name, user.email)
                }

                fun formatAuditEntry(name: String, email: String): String =
                    "audit: ${'$'}name <${'$'}email>"
            }
        """.trimIndent() + "\n")

        val result = RenameMethodRewriter.rename(
            sourceRoots = listOf(sourceDir),
            className = "com.example.services.AuditService",
            methodName = "formatAuditEntry",
            newName = "buildAuditLine",
        )

        assertTrue(result.changes.size >= 2, "Should have changes in at least 2 files. Changes: ${result.changes.map { it.filePath }}")
        val callerContent = callerFile.readText()
        assertTrue(callerContent.contains("buildAuditLine("), "Cross-file call site should be renamed. Content:\n$callerContent")
        assertTrue(!callerContent.contains("formatAuditEntry("), "Old method name should be gone from caller. Content:\n$callerContent")
        compileKotlin(tempDir)

        tempDir.deleteRecursively()
    }

    @Test
    fun `renames method in declaration and call site compiles`() {
        val tempDir = copyTestSources("rename-method-decl")
        val sourceDir = File(tempDir, "src/main/kotlin")

        val result = RenameMethodRewriter.rename(
            sourceRoots = listOf(sourceDir),
            className = "com.example.services.AuditService",
            methodName = "formatAuditEntry",
            newName = "buildAuditLine",
        )

        assertTrue(result.changes.isNotEmpty(), "Should have at least one change")
        val auditFile = File(sourceDir, "com/example/services/AuditService.kt")
        val content = auditFile.readText()
        assertTrue(content.contains("fun buildAuditLine("), "Declaration should be renamed")
        assertTrue(!content.contains("fun formatAuditEntry("), "Old method name should be gone from declaration")
        assertTrue(content.contains("buildAuditLine("), "Call site should be renamed")
        assertTrue(!content.contains("formatAuditEntry("), "Old method name should be gone from call site")

        tempDir.deleteRecursively()
    }
    @Test
    fun `RenameMethodResult JSON roundtrip preserves empty changes`() {
        val result = RenameMethodResult(emptyList())

        val json = result.toJson()
        val deserialized = RenameMethodResult.fromJson(json)

        assertEquals(result, deserialized)
    }

    @Test
    fun `RenameMethodResult JSON roundtrip preserves changes with special characters`() {
        val result = RenameMethodResult(listOf(
            RenameChange(
                filePath = "/path/to/File.kt",
                before = """val x = "hello \"world\"" """,
                after = """val y = "hello \"world\"" """,
            ),
        ))

        val json = result.toJson()
        val deserialized = RenameMethodResult.fromJson(json)

        assertEquals(result, deserialized)
    }

    @Test
    fun `RenameMethodResult JSON roundtrip preserves multi-line source code`() {
        val result = RenameMethodResult(listOf(
            RenameChange(
                filePath = "/src/main/kotlin/com/example/Service.kt",
                before = "fun greet(name: String): String {\n    return \"Hello, \$name!\"\n}",
                after = "fun sayHello(name: String): String {\n    return \"Hello, \$name!\"\n}",
            ),
            RenameChange(
                filePath = "/src/main/kotlin/com/example/Caller.kt",
                before = "service.greet(name)",
                after = "service.sayHello(name)",
            ),
        ))

        val json = result.toJson()
        val deserialized = RenameMethodResult.fromJson(json)

        assertEquals(result, deserialized)
    }
}
