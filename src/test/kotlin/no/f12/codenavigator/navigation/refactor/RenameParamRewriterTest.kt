package no.f12.codenavigator.navigation.refactor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import java.io.File
import java.nio.file.Files

class RenameParamRewriterTest {

    // [TEST] Renames parameter in method declaration
    // [TEST] Renames named argument at call site
    // [TEST] Does not rename positional arguments at call site
    // [TEST] Returns list of changed files with before/after content
    // [TEST] Leaves unrelated files unchanged
    // [TEST] Does not rename named arguments of other method calls in body
    // [TEST] Renames named arguments at cross-file call sites
    // [TEST] Detects cascade candidates when param is forwarded to same-named param
    // [TEST] No cascade candidate when called method param has different name
    // [TEST] RenameResult JSON roundtrip preserves empty changes
    // [TEST] RenameResult JSON roundtrip preserves changes with special characters
    // [TEST] RenameResult JSON roundtrip preserves multi-line source code
    // [TEST] RenameResult JSON roundtrip preserves cascade candidates

    @Test
    fun `RenameResult JSON roundtrip preserves empty changes`() {
        val result = RenameResult(emptyList())

        val json = result.toJson()
        val deserialized = RenameResult.fromJson(json)

        assertEquals(result, deserialized)
    }

    @Test
    fun `RenameResult JSON roundtrip preserves changes with special characters`() {
        val result = RenameResult(listOf(
            RenameChange(
                filePath = "/path/to/File.kt",
                before = """val x = "hello \"world\"" """,
                after = """val y = "hello \"world\"" """,
            ),
        ))

        val json = result.toJson()
        val deserialized = RenameResult.fromJson(json)

        assertEquals(result, deserialized)
    }

    @Test
    fun `RenameResult JSON roundtrip preserves multi-line source code`() {
        val result = RenameResult(listOf(
            RenameChange(
                filePath = "/src/main/kotlin/com/example/Service.kt",
                before = "fun greet(name: String): String {\n    return \"Hello, \$name!\"\n}",
                after = "fun greet(userName: String): String {\n    return \"Hello, \$userName!\"\n}",
            ),
            RenameChange(
                filePath = "/src/main/kotlin/com/example/Caller.kt",
                before = "service.greet(name = input)",
                after = "service.greet(userName = input)",
            ),
        ))

        val json = result.toJson()
        val deserialized = RenameResult.fromJson(json)

        assertEquals(result, deserialized)
    }

    @Test
    fun `RenameResult JSON roundtrip preserves cascade candidates`() {
        val result = RenameResult(
            changes = listOf(
                RenameChange(
                    filePath = "/src/main/kotlin/com/example/Service.kt",
                    before = "fun reset(usp: String) = reissue(usp)",
                    after = "fun reset(newPassword: String) = reissue(newPassword)",
                ),
            ),
            cascadeCandidates = listOf(
                CascadeCandidate(
                    className = "com.example.Service",
                    methodName = "reissue",
                    paramName = "usp",
                ),
            ),
        )

        val json = result.toJson()
        val deserialized = RenameResult.fromJson(json)

        assertEquals(result, deserialized)
    }

    @Test
    fun `renames parameter in method declaration`() {
        val sourceDir = copySourcesToTemp("rename-decl", "com/example/services", "com/example/domain")

        val result = RenameParamRewriter.rename(
            sourceRoots = listOf(sourceDir),
            className = "com.example.services.AuditService",
            methodName = "formatAuditEntry",
            paramName = "name",
            newName = "userName",
        )

        assertTrue(result.changes.isNotEmpty(), "Should have at least one change")

        val auditFile = File(sourceDir, "com/example/services/AuditService.kt")
        val content = auditFile.readText()
        assertTrue(content.contains("userName: String"), "Declaration should be renamed")
        assertTrue(!content.contains("name: String"), "Old param name should be gone from declaration")
    }

    @Test
    fun `renames named argument at call site`() {
        val sourceDir = copySourcesToTemp("rename-named-arg", "com/example/variants/namedargs", "com/example/domain")

        val result = RenameParamRewriter.rename(
            sourceRoots = listOf(sourceDir),
            className = "com.example.variants.namedargs.AuditService",
            methodName = "formatAuditEntry",
            paramName = "name",
            newName = "userName",
        )

        assertTrue(result.changes.isNotEmpty(), "Should have changes")

        val auditFile = File(sourceDir, "com/example/variants/namedargs/AuditService.kt")
        val content = auditFile.readText()
        assertTrue(content.contains("userName: String"), "Declaration should be renamed")
        assertTrue(content.contains("userName = user.name"), "Named argument should be renamed")
    }

    @Test
    fun `does not rename positional arguments at call site`() {
        val sourceDir = copySourcesToTemp("rename-positional", "com/example/services", "com/example/domain")

        val result = RenameParamRewriter.rename(
            sourceRoots = listOf(sourceDir),
            className = "com.example.services.AuditService",
            methodName = "formatAuditEntry",
            paramName = "name",
            newName = "userName",
        )

        val auditFile = File(sourceDir, "com/example/services/AuditService.kt")
        val content = auditFile.readText()
        assertTrue(content.contains("user.name, user.email"), "Positional args should be unchanged")
    }

    @Test
    fun `returns change with before and after content`() {
        val sourceDir = copySourcesToTemp("rename-change-info", "com/example/services", "com/example/domain")

        val result = RenameParamRewriter.rename(
            sourceRoots = listOf(sourceDir),
            className = "com.example.services.AuditService",
            methodName = "formatAuditEntry",
            paramName = "name",
            newName = "userName",
        )

        assertTrue(result.changes.isNotEmpty())
        val change = result.changes.first()
        assertTrue(change.before.contains("name: String"))
        assertTrue(change.after.contains("userName: String"))
        assertTrue(change.filePath.endsWith("AuditService.kt"))
    }

    @Test
    fun `leaves unrelated files unchanged`() {
        val sourceDir = copySourcesToTemp("rename-unrelated", "com/example/services", "com/example/domain")
        val domainFile = File(sourceDir, "com/example/domain/Domain.kt")
        val domainBefore = domainFile.readText()

        RenameParamRewriter.rename(
            sourceRoots = listOf(sourceDir),
            className = "com.example.services.AuditService",
            methodName = "formatAuditEntry",
            paramName = "name",
            newName = "userName",
        )

        assertEquals(domainBefore, domainFile.readText(), "Domain.kt should be unchanged")
    }

    @Test
    fun `does not rename named arguments of other method calls in body`() {
        val sourceDir = copySourcesToTemp("rename-other-named-args", "com/example/variants/othermethods", "com/example/domain")

        val result = RenameParamRewriter.rename(
            sourceRoots = listOf(sourceDir),
            className = "com.example.variants.othermethods.AuditService",
            methodName = "formatAuditEntry",
            paramName = "name",
            newName = "userName",
        )

        assertTrue(result.changes.isNotEmpty(), "Should have changes. Result: $result")

        val auditFile = File(sourceDir, "com/example/variants/othermethods/AuditService.kt")
        val content = auditFile.readText()
        assertTrue(content.contains("userName: String"), "Declaration should be renamed. Content:\n$content")
        // The value reference should be renamed: AuditEntry(name = userName, ...)
        assertTrue(content.contains("name = userName"), "Value reference should be renamed but named arg key kept. Content:\n$content")
        // The named argument key for AuditEntry constructor should NOT be renamed
        assertTrue(!content.contains("userName = userName"), "Named arg key of other method should not be renamed. Content:\n$content")
    }

    @Test
    fun `renames named arguments at cross-file call sites`() {
        val sourceDir = copySourcesToTemp("rename-cross-file", "com/example/variants/crossfilecallparam", "com/example/domain")

        val result = RenameParamRewriter.rename(
            sourceRoots = listOf(sourceDir),
            className = "com.example.variants.crossfilecallparam.AuditService",
            methodName = "formatAuditEntry",
            paramName = "name",
            newName = "userName",
        )

        assertTrue(result.changes.size >= 2, "Should have changes in at least 2 files. Changes: ${result.changes.map { it.filePath }}")

        val callerFile = File(sourceDir, "com/example/variants/crossfilecallparam/ReportService.kt")
        val callerContent = callerFile.readText()
        assertTrue(
            callerContent.contains("userName = userName"),
            "Named argument at cross-file call site should be renamed. Content:\n$callerContent",
        )
    }

    @Test
    fun `detects cascade candidates when param is forwarded to same-named param`() {
        val sourceDir = copySourcesToTemp("rename-cascade", "com/example/variants/cascade", "com/example/domain")

        val result = RenameParamRewriter.rename(
            sourceRoots = listOf(sourceDir),
            className = "com.example.variants.cascade.AuditService",
            methodName = "formatAuditEntry",
            paramName = "name",
            newName = "userName",
        )

        assertTrue(result.cascadeCandidates.isNotEmpty(), "Should detect cascade candidate. Changes: ${result.changes.map { it.filePath }}, cascadeCandidates: ${result.cascadeCandidates}")
        val candidate = result.cascadeCandidates.first()
        assertEquals("buildLine", candidate.methodName)
        assertEquals("name", candidate.paramName)
    }

    @Test
    fun `no cascade candidate when called method param has different name`() {
        val sourceDir = copySourcesToTemp("rename-no-cascade", "com/example/variants/nocascade", "com/example/domain")

        val result = RenameParamRewriter.rename(
            sourceRoots = listOf(sourceDir),
            className = "com.example.variants.nocascade.AuditService",
            methodName = "formatAuditEntry",
            paramName = "name",
            newName = "userName",
        )

        assertTrue(result.cascadeCandidates.isEmpty(), "Should NOT detect cascade candidate when param names differ. cascadeCandidates: ${result.cascadeCandidates}")
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
