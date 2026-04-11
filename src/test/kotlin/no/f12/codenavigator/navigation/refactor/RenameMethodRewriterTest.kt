package no.f12.codenavigator.navigation.refactor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import java.io.File
import java.nio.file.Files

class RenameMethodRewriterTest {

    private val testProjectSrc = File("test-project/src/main/kotlin")

    companion object {
        private val cachedParsedSources by lazy {
            parseKotlinSources(listOf(File("test-project/src/main/kotlin")))
        }
    }

    @Test
    fun `returns change with before and after content`() {
        val result = RenameMethodRewriter.rename(
            sourceRoots = listOf(testProjectSrc),
            className = "com.example.services.AuditService",
            methodName = "formatAuditEntry",
            newName = "buildAuditLine",
            preview = true,
            parsedSources = cachedParsedSources,
        )

        assertTrue(result.changes.isNotEmpty())
        val change = result.changes.first()
        assertTrue(change.before.contains("formatAuditEntry"))
        assertTrue(change.after.contains("buildAuditLine"))
        assertTrue(change.filePath.endsWith("AuditService.kt"))
    }

    @Test
    fun `leaves unrelated files unchanged`() {
        val result = RenameMethodRewriter.rename(
            sourceRoots = listOf(testProjectSrc),
            className = "com.example.services.AuditService",
            methodName = "formatAuditEntry",
            newName = "buildAuditLine",
            preview = true,
            parsedSources = cachedParsedSources,
        )

        val changedFiles = result.changes.map { it.filePath }
        assertTrue(
            changedFiles.none { it.endsWith("Domain.kt") },
            "Domain.kt should not appear in changes. Changed files: $changedFiles",
        )
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
        val result = RenameMethodRewriter.rename(
            sourceRoots = listOf(testProjectSrc),
            className = "com.example.domain.UserRepository",
            methodName = "findById",
            newName = "lookupById",
            preview = true,
            parsedSources = cachedParsedSources,
        )

        assertTrue(result.changes.isNotEmpty(), "Should have changes. Result: $result")

        val domainChange = result.changes.first { it.filePath.endsWith("Domain.kt") }
        assertTrue(domainChange.after.contains("fun lookupById("), "Interface method should be renamed. Content:\n${domainChange.after}")
        assertTrue(!domainChange.after.contains("fun findById("), "Old method name should be gone from interface. Content:\n${domainChange.after}")

        val implChange = result.changes.first { it.filePath.endsWith("InMemoryUserRepository.kt") }
        assertTrue(implChange.after.contains("fun lookupById("), "Implementation should be renamed. Content:\n${implChange.after}")
        assertTrue(!implChange.after.contains("fun findById("), "Old method name should be gone from implementation. Content:\n${implChange.after}")

        val auditChange = result.changes.first { it.filePath.endsWith("services/AuditService.kt") }
        assertTrue(auditChange.after.contains("lookupById("), "Call site should be renamed. Content:\n${auditChange.after}")
        assertTrue(!auditChange.after.contains("findById("), "Old method name should be gone from call site. Content:\n${auditChange.after}")
    }

    @Test
    fun `renames method at cross-file call site`() {
        val result = RenameMethodRewriter.rename(
            sourceRoots = listOf(testProjectSrc),
            className = "com.example.variants.crossfilecallmethod.AuditService",
            methodName = "formatAuditEntry",
            newName = "buildAuditLine",
            preview = true,
            parsedSources = cachedParsedSources,
        )

        assertTrue(result.changes.size >= 2, "Should have changes in at least 2 files. Changes: ${result.changes.map { it.filePath }}")

        val callerChange = result.changes.first { it.filePath.endsWith("crossfilecallmethod/ReportService.kt") }
        assertTrue(callerChange.after.contains("buildAuditLine("), "Cross-file call site should be renamed. Content:\n${callerChange.after}")
        assertTrue(!callerChange.after.contains("formatAuditEntry("), "Old method name should be gone from caller. Content:\n${callerChange.after}")
    }

    @Test
    fun `renames method in declaration and call site`() {
        val result = RenameMethodRewriter.rename(
            sourceRoots = listOf(testProjectSrc),
            className = "com.example.services.AuditService",
            methodName = "formatAuditEntry",
            newName = "buildAuditLine",
            preview = true,
            parsedSources = cachedParsedSources,
        )

        assertTrue(result.changes.isNotEmpty(), "Should have at least one change")
        val change = result.changes.first { it.filePath.endsWith("services/AuditService.kt") }
        assertTrue(change.after.contains("fun buildAuditLine("), "Declaration should be renamed")
        assertTrue(!change.after.contains("fun formatAuditEntry("), "Old method name should be gone from declaration")
        assertTrue(change.after.contains("buildAuditLine("), "Call site should be renamed")
        assertTrue(!change.after.contains("formatAuditEntry("), "Old method name should be gone from call site")
    }

    @Test
    fun `renames companion object method declaration using outer class name`() {
        val result = RenameMethodRewriter.rename(
            sourceRoots = listOf(testProjectSrc),
            className = "com.example.variants.companion.UserFactory",
            methodName = "create",
            newName = "build",
            preview = true,
            parsedSources = cachedParsedSources,
        )

        assertTrue(result.changes.isNotEmpty(), "Should have changes for companion method. Changes: ${result.changes.map { it.filePath }}")
        val factoryChange = result.changes.first { it.filePath.endsWith("UserFactory.kt") }
        assertTrue(factoryChange.after.contains("fun build("), "Companion method declaration should be renamed. Content:\n${factoryChange.after}")
        assertTrue(!factoryChange.after.contains("fun create("), "Old method name should be gone from companion. Content:\n${factoryChange.after}")
    }

    @Test
    fun `renames companion object method at cross-file call site`() {
        val result = RenameMethodRewriter.rename(
            sourceRoots = listOf(testProjectSrc),
            className = "com.example.variants.companion.UserFactory",
            methodName = "create",
            newName = "build",
            preview = true,
            parsedSources = cachedParsedSources,
        )

        val callerChange = result.changes.firstOrNull { it.filePath.endsWith("companion/UserService.kt") }
        assertTrue(callerChange != null, "UserService call site should be updated. Changes: ${result.changes.map { it.filePath }}")
        assertTrue(callerChange.after.contains(".build("), "Call site should use new name. Content:\n${callerChange.after}")
        assertTrue(!callerChange.after.contains(".create("), "Old method name should be gone from call site. Content:\n${callerChange.after}")
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
