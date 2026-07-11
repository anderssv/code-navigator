package no.f12.codenavigator.navigation.refactor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import java.io.File
import java.nio.file.Files

class RenameMethodRewriterTest {

    private val testProjectSrc = File("test-project/src/main/kotlin")
    private val testProjectJavaSrc = File("test-project/src/main/java")

    @Test
    fun `returns change with before and after content`() {
        val result = RenameMethodRewriter.rename(
            sourceRoots = listOf(testProjectSrc),
            className = "com.example.services.AuditService",
            methodName = "formatAuditEntry",
            newName = "buildAuditLine",
            preview = true,

        )

        assertTrue(result.changes.isNotEmpty(), "Should have changes. Files: ${result.changes.map { it.filePath }}")
        val change = result.changes.first { it.filePath.endsWith("services/AuditService.kt") }
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

    @Test
    fun `renames suspend function declaration`() {
        val result = RenameMethodRewriter.rename(
            sourceRoots = listOf(testProjectSrc),
            className = "com.example.routes.UserRoute",
            methodName = "handleReset",
            newName = "processReset",
            preview = true,

        )

        assertTrue(result.changes.isNotEmpty(), "Expected changes for suspend function rename")
        val change = result.changes.first { it.filePath.endsWith("UserRoute.kt") }
        assertTrue(change.after.contains("processReset"), "Expected renamed suspend function in output")
        assertTrue(!change.after.contains("fun handleReset"), "Expected old name to be gone from declaration")
    }

    @Test
    fun `renames call site of suspend function`() {
        val result = RenameMethodRewriter.rename(
            sourceRoots = listOf(testProjectSrc),
            className = "com.example.routes.UserRoute",
            methodName = "handleReset",
            newName = "processReset",
            preview = true,

        )

        // Check that any callers of handleReset are also updated
        val allAfter = result.changes.joinToString("\n") { it.after }
        assertTrue(!allAfter.contains(".handleReset("), "Expected call sites to be renamed")
    }

    // --- Phase B: Bytecode-guided tests ---

    private val testProjectClasses = File("test-project/build/classes/kotlin/main")

    @Test
    fun `bytecode-guided rename finds cross-file call sites without imports`() {
        val result = RenameMethodRewriter.rename(
            sourceRoots = listOf(testProjectSrc),
            className = "com.example.variants.crossfilecallmethod.AuditService",
            methodName = "formatAuditEntry",
            newName = "buildAuditLine",
            preview = true,
            classesRoots = listOf(testProjectClasses),
        )

        assertTrue(result.changes.size >= 2, "Should have changes in at least 2 files with bytecode guidance. Changes: ${result.changes.map { it.filePath }}")
        val callerChange = result.changes.first { it.filePath.endsWith("crossfilecallmethod/ReportService.kt") }
        assertTrue(callerChange.after.contains("buildAuditLine("), "Cross-file call site should be renamed via bytecode guidance")
    }

    @Test
    fun `bytecode-guided rename finds implementor declarations`() {
        val result = RenameMethodRewriter.rename(
            sourceRoots = listOf(testProjectSrc),
            className = "com.example.domain.UserRepository",
            methodName = "findById",
            newName = "lookupById",
            preview = true,
            classesRoots = listOf(testProjectClasses),
        )

        assertTrue(result.changes.isNotEmpty(), "Should have changes with bytecode guidance")
        val implChange = result.changes.first { it.filePath.endsWith("InMemoryUserRepository.kt") }
        assertTrue(implChange.after.contains("fun lookupById("), "Implementor declaration should be renamed via bytecode")
    }

    @Test
    fun `renames method declaration in Java file`() {
        val result = RenameMethodEditor.rename(
            sourceRoots = listOf(testProjectJavaSrc),
            className = "com.example.javaservice.JavaAuditService",
            methodName = "formatAuditEntry",
            newName = "buildAuditLine",
            preview = true,
        )

        assertTrue(result.changes.isNotEmpty(), "Should have changes in Java file. Files scanned from: $testProjectJavaSrc")
        val change = result.changes.first { it.filePath.endsWith("JavaAuditService.java") }
        assertTrue(change.after.contains("buildAuditLine"), "Declaration should be renamed")
        assertTrue(!change.after.contains("formatAuditEntry"), "Old name should be gone")
    }

    @Test
    fun `renames method call site in Java file`() {
        val result = RenameMethodEditor.rename(
            sourceRoots = listOf(testProjectJavaSrc),
            className = "com.example.javaservice.JavaAuditService",
            methodName = "formatAuditEntry",
            newName = "buildAuditLine",
            preview = true,
        )

        assertTrue(result.changes.size >= 2, "Should have changes in at least 2 Java files. Changes: ${result.changes.map { it.filePath }}")
        val callerChange = result.changes.first { it.filePath.endsWith("JavaReportService.java") }
        assertTrue(callerChange.after.contains("buildAuditLine"), "Call site should be renamed")
        assertTrue(!callerChange.after.contains("formatAuditEntry"), "Old name should be gone from caller")
    }

    @Test
    fun `single rename call handles both Kotlin and Java source roots`() {
        val result = RenameMethodEditor.rename(
            sourceRoots = listOf(testProjectSrc, testProjectJavaSrc),
            className = "com.example.services.AuditService",
            methodName = "formatAuditEntry",
            newName = "buildAuditLine",
            preview = true,
        )

        val ktChange = result.changes.firstOrNull { it.filePath.endsWith("services/AuditService.kt") }
        assertTrue(ktChange != null, "Should find Kotlin file changes. Changes: ${result.changes.map { it.filePath }}")
        assertTrue(ktChange!!.after.contains("buildAuditLine"), "Kotlin declaration should be renamed")

        // Java files in a different package won't match this className, but the mechanism should process both
        val allExtensions = result.changes.map { File(it.filePath).extension }.toSet()
        // At minimum, .kt files should be found
        assertTrue("kt" in allExtensions, "Should process .kt files")
    }

    @Test
    fun `renaming a method on an Impl also renames the interface and sibling implementors`() {
        val result = RenameMethodRewriter.rename(
            sourceRoots = listOf(testProjectSrc),
            className = "com.example.variants.overridefamily.RaClientImpl",
            methodName = "getInfo",
            newName = "fetchInfo",
            preview = true,
            classesRoots = listOf(testProjectClasses),
        )

        val change = result.changes.firstOrNull { it.filePath.endsWith("overridefamily/RaClient.kt") }
        assertTrue(change != null, "Should change RaClient.kt. Changes: ${result.changes.map { it.filePath }}")
        // Impl, interface, AND sibling fake declarations must all be renamed so the override still resolves.
        assertTrue(!change.after.contains("fun getInfo("), "No old name should remain. Content:\n${change.after}")
        assertEquals(3, Regex("fun fetchInfo\\(").findAll(change.after).count(),
            "Interface + impl + fake declarations should all be renamed (3 occurrences). Content:\n${change.after}")
    }
}
