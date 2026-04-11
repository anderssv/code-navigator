package no.f12.codenavigator.navigation.refactor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import java.io.File
import java.nio.file.Files

class RenameParamRewriterTest {

    private val testProjectSrc = File("test-project/src/main/kotlin")

    companion object {
        private val cachedParsedSources by lazy {
            parseKotlinSources(listOf(File("test-project/src/main/kotlin")))
        }
    }

    // [TEST] Renames parameter in method declaration
    // [TEST] Renames named argument at call site
    // [TEST] Does not rename positional arguments at call site
    // [TEST] Returns list of changed files with before/after content
    // [TEST] Leaves unrelated files unchanged
    // [TEST] Preview mode does not write to disk
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
    fun `RenameResult JSON roundtrip preserves warnings`() {
        val result = RenameResult(
            changes = listOf(
                RenameChange(
                    filePath = "/src/main/kotlin/com/example/Data.kt",
                    before = "data class Data(val name: String)",
                    after = "data class Data(val fullName: String)",
                ),
            ),
            warnings = listOf("WARNING: Parameter 'name' is a val/var constructor property."),
        )

        val json = result.toJson()
        val deserialized = RenameResult.fromJson(json)

        assertEquals(result, deserialized)
    }

    @Test
    fun `renames parameter in method declaration`() {
        val result = RenameParamRewriter.rename(
            sourceRoots = listOf(testProjectSrc),
            className = "com.example.services.AuditService",
            methodName = "formatAuditEntry",
            paramName = "name",
            newName = "userName",
            preview = true,
            parsedSources = cachedParsedSources,
        )

        assertTrue(result.changes.isNotEmpty(), "Should have at least one change")
        val change = result.changes.first { it.filePath.endsWith("AuditService.kt") }
        assertTrue(change.after.contains("userName: String"), "Declaration should be renamed")
        assertTrue(!change.after.contains("name: String"), "Old param name should be gone from declaration")
    }

    @Test
    fun `renames named argument at call site`() {
        val result = RenameParamRewriter.rename(
            sourceRoots = listOf(testProjectSrc),
            className = "com.example.variants.namedargs.AuditService",
            methodName = "formatAuditEntry",
            paramName = "name",
            newName = "userName",
            preview = true,
            parsedSources = cachedParsedSources,
        )

        assertTrue(result.changes.isNotEmpty(), "Should have changes")
        val change = result.changes.first { it.filePath.endsWith("namedargs/AuditService.kt") }
        assertTrue(change.after.contains("userName: String"), "Declaration should be renamed")
        assertTrue(change.after.contains("userName = user.name"), "Named argument should be renamed")
    }

    @Test
    fun `does not rename positional arguments at call site`() {
        val result = RenameParamRewriter.rename(
            sourceRoots = listOf(testProjectSrc),
            className = "com.example.services.AuditService",
            methodName = "formatAuditEntry",
            paramName = "name",
            newName = "userName",
            preview = true,
            parsedSources = cachedParsedSources,
        )

        val change = result.changes.first { it.filePath.endsWith("services/AuditService.kt") }
        assertTrue(change.after.contains("user.name, user.email"), "Positional args should be unchanged")
    }

    @Test
    fun `returns change with before and after content`() {
        val result = RenameParamRewriter.rename(
            sourceRoots = listOf(testProjectSrc),
            className = "com.example.services.AuditService",
            methodName = "formatAuditEntry",
            paramName = "name",
            newName = "userName",
            preview = true,
            parsedSources = cachedParsedSources,
        )

        assertTrue(result.changes.isNotEmpty())
        val change = result.changes.first()
        assertTrue(change.before.contains("name: String"))
        assertTrue(change.after.contains("userName: String"))
        assertTrue(change.filePath.endsWith("AuditService.kt"))
    }

    @Test
    fun `leaves unrelated files unchanged`() {
        val result = RenameParamRewriter.rename(
            sourceRoots = listOf(testProjectSrc),
            className = "com.example.services.AuditService",
            methodName = "formatAuditEntry",
            paramName = "name",
            newName = "userName",
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
        val sourceDir = copySourcesToTemp("rename-param-preview", "com/example/services", "com/example/domain")
        val auditFile = File(sourceDir, "com/example/services/AuditService.kt")
        val originalContent = auditFile.readText()

        val result = RenameParamRewriter.rename(
            sourceRoots = listOf(sourceDir),
            className = "com.example.services.AuditService",
            methodName = "formatAuditEntry",
            paramName = "name",
            newName = "userName",
            preview = true,
        )

        assertTrue(result.changes.isNotEmpty(), "Should detect changes")
        assertEquals(originalContent, auditFile.readText(), "File should not be modified in preview mode")
    }

    @Test
    fun `does not rename named arguments of other method calls in body`() {
        val result = RenameParamRewriter.rename(
            sourceRoots = listOf(testProjectSrc),
            className = "com.example.variants.othermethods.AuditService",
            methodName = "formatAuditEntry",
            paramName = "name",
            newName = "userName",
            preview = true,
            parsedSources = cachedParsedSources,
        )

        assertTrue(result.changes.isNotEmpty(), "Should have changes. Result: $result")
        val change = result.changes.first { it.filePath.endsWith("othermethods/AuditService.kt") }
        assertTrue(change.after.contains("userName: String"), "Declaration should be renamed. Content:\n${change.after}")
        assertTrue(change.after.contains("name = userName"), "Value reference should be renamed but named arg key kept. Content:\n${change.after}")
        assertTrue(!change.after.contains("userName = userName"), "Named arg key of other method should not be renamed. Content:\n${change.after}")
    }

    @Test
    fun `renames named arguments at cross-file call sites`() {
        val result = RenameParamRewriter.rename(
            sourceRoots = listOf(testProjectSrc),
            className = "com.example.variants.crossfilecallparam.AuditService",
            methodName = "formatAuditEntry",
            paramName = "name",
            newName = "userName",
            preview = true,
            parsedSources = cachedParsedSources,
        )

        assertTrue(result.changes.size >= 2, "Should have changes in at least 2 files. Changes: ${result.changes.map { it.filePath }}")
        val callerChange = result.changes.first { it.filePath.endsWith("ReportService.kt") }
        assertTrue(
            callerChange.after.contains("userName = userName"),
            "Named argument at cross-file call site should be renamed. Content:\n${callerChange.after}",
        )
    }

    @Test
    fun `detects cascade candidates when param is forwarded to same-named param`() {
        val result = RenameParamRewriter.rename(
            sourceRoots = listOf(testProjectSrc),
            className = "com.example.variants.cascade.AuditService",
            methodName = "formatAuditEntry",
            paramName = "name",
            newName = "userName",
            preview = true,
            parsedSources = cachedParsedSources,
        )

        assertTrue(result.cascadeCandidates.isNotEmpty(), "Should detect cascade candidate. Changes: ${result.changes.map { it.filePath }}, cascadeCandidates: ${result.cascadeCandidates}")
        val candidate = result.cascadeCandidates.first()
        assertEquals("buildLine", candidate.methodName)
        assertEquals("name", candidate.paramName)
    }

    @Test
    fun `no cascade candidate when called method param has different name`() {
        val result = RenameParamRewriter.rename(
            sourceRoots = listOf(testProjectSrc),
            className = "com.example.variants.nocascade.AuditService",
            methodName = "formatAuditEntry",
            paramName = "name",
            newName = "userName",
            preview = true,
            parsedSources = cachedParsedSources,
        )

        assertTrue(result.cascadeCandidates.isEmpty(), "Should NOT detect cascade candidate when param names differ. cascadeCandidates: ${result.cascadeCandidates}")
    }

    @Test
    fun `renames companion object method param using outer class name`() {
        val result = RenameParamRewriter.rename(
            sourceRoots = listOf(testProjectSrc),
            className = "com.example.variants.companion.UserFactory",
            methodName = "create",
            paramName = "name",
            newName = "fullName",
            preview = true,
            parsedSources = cachedParsedSources,
        )

        assertTrue(result.changes.isNotEmpty(), "Should have changes for companion method param. Changes: ${result.changes.map { it.filePath }}")
        val factoryChange = result.changes.first { it.filePath.endsWith("UserFactory.kt") }
        assertTrue(factoryChange.after.contains("fullName: String"), "Companion method param should be renamed. Content:\n${factoryChange.after}")
    }

    @Test
    fun `renames named argument at companion object call site`() {
        val result = RenameParamRewriter.rename(
            sourceRoots = listOf(testProjectSrc),
            className = "com.example.variants.companion.UserFactory",
            methodName = "create",
            paramName = "name",
            newName = "fullName",
            preview = true,
            parsedSources = cachedParsedSources,
        )

        val callerChange = result.changes.firstOrNull { it.filePath.endsWith("companion/UserService.kt") }
        assertTrue(callerChange != null, "UserService should have changes. Changes: ${result.changes.map { it.filePath }}")
        assertTrue(callerChange.after.contains("fullName = name"), "Named argument at call site should be renamed. Content:\n${callerChange.after}")
        assertTrue(!callerChange.after.contains("name = name"), "Old named argument should be gone. Content:\n${callerChange.after}")
    }

    @Test
    fun `returns warning for constructor val param rename`() {
        val result = RenameParamRewriter.rename(
            sourceRoots = listOf(testProjectSrc),
            className = "com.example.variants.constructorparam.Registration",
            methodName = "<constructor>",
            paramName = "fullName",
            newName = "displayName",
            preview = true,
            parsedSources = cachedParsedSources,
        )

        assertTrue(result.warnings.isNotEmpty(), "Should have a warning for val constructor param. Warnings: ${result.warnings}")
        assertTrue(result.warnings.any { it.contains("property") }, "Warning should mention property access. Warnings: ${result.warnings}")
    }

    @Test
    fun `no warning for regular method param rename`() {
        val result = RenameParamRewriter.rename(
            sourceRoots = listOf(testProjectSrc),
            className = "com.example.services.AuditService",
            methodName = "formatAuditEntry",
            paramName = "name",
            newName = "userName",
            preview = true,
            parsedSources = cachedParsedSources,
        )

        assertTrue(result.warnings.isEmpty(), "Should have no warnings for regular method param. Warnings: ${result.warnings}")
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
