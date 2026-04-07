package no.f12.codenavigator.navigation.refactor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import java.io.File

class RenameParamRewriterTest {

    // [TEST] Renames parameter in method declaration
    // [TEST] Renames named argument at call site
    // [TEST] Does not rename positional arguments at call site
    // [TEST] Source compiles before and after rename
    // [TEST] Returns list of changed files with before/after content
    // [TEST] Handles multiple call sites with named arguments
    // [TEST] Leaves unrelated files unchanged
    // [TEST] RenameResult JSON roundtrip preserves empty changes
    // [TEST] RenameResult JSON roundtrip preserves changes with special characters
    // [TEST] RenameResult JSON roundtrip preserves multi-line source code

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
        val tempDir = copyTestSources("rename-decl")

        val sourceDir = File(tempDir, "src/main/kotlin")

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

        tempDir.deleteRecursively()
    }

    @Test
    fun `renames named argument at call site`() {
        val tempDir = copyTestSources("rename-named-arg")

        val sourceDir = File(tempDir, "src/main/kotlin")
        val auditFile = File(sourceDir, "com/example/services/AuditService.kt")
        auditFile.writeText("""
            package com.example.services

            import com.example.domain.UserRepository

            class AuditService(
                private val repository: UserRepository,
            ) {
                fun auditUser(userId: String): String {
                    val user = repository.findById(userId) ?: return "not found"
                    return formatAuditEntry(name = user.name, email = user.email)
                }

                private fun formatAuditEntry(name: String, email: String): String =
                    "audit: ${'$'}name <${'$'}email>"
            }
        """.trimIndent() + "\n")

        val result = RenameParamRewriter.rename(
            sourceRoots = listOf(sourceDir),
            className = "com.example.services.AuditService",
            methodName = "formatAuditEntry",
            paramName = "name",
            newName = "userName",
        )

        assertTrue(result.changes.isNotEmpty(), "Should have changes")

        val content = auditFile.readText()
        assertTrue(content.contains("userName: String"), "Declaration should be renamed")
        assertTrue(content.contains("userName = user.name"), "Named argument should be renamed")

        tempDir.deleteRecursively()
    }

    @Test
    fun `does not rename positional arguments at call site`() {
        val tempDir = copyTestSources("rename-positional")

        val sourceDir = File(tempDir, "src/main/kotlin")

        val result = RenameParamRewriter.rename(
            sourceRoots = listOf(sourceDir),
            className = "com.example.services.AuditService",
            methodName = "formatAuditEntry",
            paramName = "name",
            newName = "userName",
        )

        val auditFile = File(sourceDir, "com/example/services/AuditService.kt")
        val content = auditFile.readText()
        // The call site uses positional args: formatAuditEntry(user.name, user.email)
        // This should NOT be changed
        assertTrue(content.contains("user.name, user.email"), "Positional args should be unchanged")

        tempDir.deleteRecursively()
    }

    @Test
    fun `returns change with before and after content`() {
        val tempDir = copyTestSources("rename-change-info")

        val sourceDir = File(tempDir, "src/main/kotlin")

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

        tempDir.deleteRecursively()
    }

    @Test
    fun `leaves unrelated files unchanged`() {
        val tempDir = copyTestSources("rename-unrelated")

        val sourceDir = File(tempDir, "src/main/kotlin")
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

        tempDir.deleteRecursively()
    }

    @Test
    fun `does not rename named arguments of other method calls in body`() {
        val tempDir = copyTestSources("rename-other-named-args")

        val sourceDir = File(tempDir, "src/main/kotlin")
        val auditFile = File(sourceDir, "com/example/services/AuditService.kt")
        // Add a method that calls a data class constructor using named arguments
        // where the named arg key matches the parameter being renamed
        auditFile.writeText("""
            package com.example.services

            import com.example.domain.UserRepository

            data class AuditEntry(val name: String, val email: String)

            class AuditService(
                private val repository: UserRepository,
            ) {
                fun auditUser(userId: String): String {
                    val user = repository.findById(userId) ?: return "not found"
                    return formatAuditEntry(user.name, user.email).toString()
                }

                fun formatAuditEntry(name: String, email: String): AuditEntry =
                    AuditEntry(name = name, email = email)
            }
        """.trimIndent() + "\n")

        val result = RenameParamRewriter.rename(
            sourceRoots = listOf(sourceDir),
            className = "com.example.services.AuditService",
            methodName = "formatAuditEntry",
            paramName = "name",
            newName = "userName",
        )

        assertTrue(result.changes.isNotEmpty(), "Should have changes. Result: $result")

        val content = auditFile.readText()
        assertTrue(content.contains("userName: String"), "Declaration should be renamed. Content:\n$content")
        // The value reference should be renamed: AuditEntry(name = userName, ...)
        assertTrue(content.contains("name = userName"), "Value reference should be renamed but named arg key kept. Content:\n$content")
        // The named argument key for AuditEntry constructor should NOT be renamed
        assertTrue(!content.contains("userName = userName"), "Named arg key of other method should not be renamed. Content:\n$content")

        compileKotlin(tempDir)

        tempDir.deleteRecursively()
    }

    @Test
    fun `renames named arguments at cross-file call sites`() {
        val tempDir = copyTestSources("rename-unresolved-type")

        val sourceDir = File(tempDir, "src/main/kotlin")
        val auditFile = File(sourceDir, "com/example/services/AuditService.kt")
        // Add method with a parameter and a cross-file caller
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

        // Add a caller in a different file that uses named arguments
        val callerFile = File(sourceDir, "com/example/services/ReportService.kt")
        callerFile.parentFile.mkdirs()
        callerFile.writeText("""
            package com.example.services

            class ReportService(
                private val auditService: AuditService,
            ) {
                fun generateReport(userName: String, userEmail: String): String =
                    auditService.formatAuditEntry(name = userName, email = userEmail)
            }
        """.trimIndent() + "\n")

        val result = RenameParamRewriter.rename(
            sourceRoots = listOf(sourceDir),
            className = "com.example.services.AuditService",
            methodName = "formatAuditEntry",
            paramName = "name",
            newName = "userName",
        )

        assertTrue(result.changes.size >= 2, "Should have changes in at least 2 files. Changes: ${result.changes.map { it.filePath }}")

        val callerContent = callerFile.readText()
        assertTrue(
            callerContent.contains("userName = userName"),
            "Named argument at cross-file call site should be renamed. Content:\n$callerContent",
        )

        compileKotlin(tempDir)

        tempDir.deleteRecursively()
    }

    @Test
    fun `detects cascade candidates when param is forwarded to same-named param`() {
        val tempDir = copyTestSources("rename-cascade")

        val sourceDir = File(tempDir, "src/main/kotlin")
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

                fun formatAuditEntry(name: String, email: String): String {
                    return buildLine(name, email)
                }

                private fun buildLine(name: String, email: String): String =
                    "audit: ${'$'}name <${'$'}email>"
            }
        """.trimIndent() + "\n")

        val result = RenameParamRewriter.rename(
            sourceRoots = listOf(sourceDir),
            className = "com.example.services.AuditService",
            methodName = "formatAuditEntry",
            paramName = "name",
            newName = "userName",
        )

        assertTrue(result.cascadeCandidates.isNotEmpty(), "Should detect cascade candidate. Changes: ${result.changes.map { it.filePath }}, cascadeCandidates: ${result.cascadeCandidates}")
        val candidate = result.cascadeCandidates.first()
        assertEquals("buildLine", candidate.methodName)
        assertEquals("name", candidate.paramName)

        tempDir.deleteRecursively()
    }

    @Test
    fun `no cascade candidate when called method param has different name`() {
        val tempDir = copyTestSources("rename-no-cascade")

        val sourceDir = File(tempDir, "src/main/kotlin")
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

                fun formatAuditEntry(name: String, email: String): String {
                    return buildLine(name, email)
                }

                private fun buildLine(label: String, email: String): String =
                    "audit: ${'$'}label <${'$'}email>"
            }
        """.trimIndent() + "\n")

        val result = RenameParamRewriter.rename(
            sourceRoots = listOf(sourceDir),
            className = "com.example.services.AuditService",
            methodName = "formatAuditEntry",
            paramName = "name",
            newName = "userName",
        )

        assertTrue(result.cascadeCandidates.isEmpty(), "Should NOT detect cascade candidate when param names differ. cascadeCandidates: ${result.cascadeCandidates}")

        tempDir.deleteRecursively()
    }
}
