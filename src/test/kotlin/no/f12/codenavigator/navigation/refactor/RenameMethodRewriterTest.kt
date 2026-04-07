package no.f12.codenavigator.navigation.refactor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import java.io.File
import java.nio.file.Files

class RenameMethodRewriterTest {

    @Test
    fun `returns change with before and after content`() {
        val sourceDir = copySourcesToTemp("rename-method-change-info", "com/example/services", "com/example/domain")

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
    }

    @Test
    fun `leaves unrelated files unchanged`() {
        val sourceDir = copySourcesToTemp("rename-method-unrelated", "com/example/services", "com/example/domain")
        val domainFile = File(sourceDir, "com/example/domain/Domain.kt")
        val domainBefore = domainFile.readText()

        RenameMethodRewriter.rename(
            sourceRoots = listOf(sourceDir),
            className = "com.example.services.AuditService",
            methodName = "formatAuditEntry",
            newName = "buildAuditLine",
        )

        assertEquals(domainBefore, domainFile.readText(), "Domain.kt should be unchanged")
    }

    @Test
    fun `preview mode does not write to disk`() {
        val sourceDir = copySourcesToTemp("rename-method-preview", "com/example/services", "com/example/domain")
        val auditFile = File(sourceDir, "com/example/services/AuditService.kt")
        val originalContent = auditFile.readText()

        val result = RenameMethodRewriter.rename(
            sourceRoots = listOf(sourceDir),
            className = "com.example.services.AuditService",
            methodName = "formatAuditEntry",
            newName = "buildAuditLine",
            preview = true,
        )

        assertTrue(result.changes.isNotEmpty(), "Should detect changes")
        assertEquals(originalContent, auditFile.readText(), "File should not be modified in preview mode")
    }

    @Test
    fun `renames method called via interface type`() {
        val sourceDir = copySourcesToTemp(
            "rename-method-interface",
            "com/example/services",
            "com/example/domain",
            "com/example/infra",
        )

        val result = RenameMethodRewriter.rename(
            sourceRoots = listOf(sourceDir),
            className = "com.example.domain.UserRepository",
            methodName = "findById",
            newName = "lookupById",
        )

        assertTrue(result.changes.isNotEmpty(), "Should have changes. Result: $result")

        val domainFile = File(sourceDir, "com/example/domain/Domain.kt")
        val domainContent = domainFile.readText()
        assertTrue(domainContent.contains("fun lookupById("), "Interface method should be renamed. Content:\n$domainContent")
        assertTrue(!domainContent.contains("fun findById("), "Old method name should be gone from interface. Content:\n$domainContent")

        val implFile = File(sourceDir, "com/example/infra/InMemoryUserRepository.kt")
        val implContent = implFile.readText()
        assertTrue(implContent.contains("fun lookupById("), "Implementation should be renamed. Content:\n$implContent")

        val auditFile = File(sourceDir, "com/example/services/AuditService.kt")
        val auditContent = auditFile.readText()
        assertTrue(auditContent.contains("lookupById("), "Call site should be renamed. Content:\n$auditContent")
        assertTrue(!auditContent.contains("findById("), "Old method name should be gone from call site. Content:\n$auditContent")
    }

    @Test
    fun `renames method at cross-file call site`() {
        val sourceDir = copySourcesToTemp(
            "rename-method-cross",
            "com/example/variants/crossfilecallmethod",
            "com/example/domain",
        )

        val result = RenameMethodRewriter.rename(
            sourceRoots = listOf(sourceDir),
            className = "com.example.variants.crossfilecallmethod.AuditService",
            methodName = "formatAuditEntry",
            newName = "buildAuditLine",
        )

        assertTrue(result.changes.size >= 2, "Should have changes in at least 2 files. Changes: ${result.changes.map { it.filePath }}")

        val callerFile = File(sourceDir, "com/example/variants/crossfilecallmethod/ReportService.kt")
        val callerContent = callerFile.readText()
        assertTrue(callerContent.contains("buildAuditLine("), "Cross-file call site should be renamed. Content:\n$callerContent")
        assertTrue(!callerContent.contains("formatAuditEntry("), "Old method name should be gone from caller. Content:\n$callerContent")
    }

    @Test
    fun `renames method in declaration and call site`() {
        val sourceDir = copySourcesToTemp("rename-method-decl", "com/example/services", "com/example/domain")

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

    private fun copySourcesToTemp(label: String, vararg packages: String): File {
        val testProjectSrc = File("test-project/src/main/kotlin")
        val tempDir = Files.createTempDirectory("cnav-test-$label").toFile()

        for (pkg in packages) {
            val srcPkg = File(testProjectSrc, pkg)
            val destPkg = File(tempDir, pkg)
            srcPkg.copyRecursively(destPkg)
        }

        return tempDir
    }
}
