package no.f12.codenavigator.navigation.refactor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import java.io.File
import java.nio.file.Files

class RenamePropertyRewriterTest {

    private val testProjectSrc = File("test-project/src/main/kotlin")

    @Test
    fun `renames val constructor param declaration`() {
        val result = RenamePropertyRewriter.rename(
            sourceRoots = listOf(testProjectSrc),
            className = "com.example.variants.property.UserProfile",
            propertyName = "fullName",
            newName = "displayName",
            preview = true,
        )

        assertTrue(result.changes.isNotEmpty(), "Should have at least one change")
        val change = result.changes.first { it.filePath.endsWith("UserProfile.kt") }
        assertTrue(change.after.contains("val displayName: String"), "Constructor param declaration should be renamed")
        assertTrue(!change.after.contains("val fullName: String"), "Old property name should be gone from declaration")
    }

    @Test
    fun `renames property access site`() {
        val result = RenamePropertyRewriter.rename(
            sourceRoots = listOf(testProjectSrc),
            className = "com.example.variants.property.UserProfile",
            propertyName = "fullName",
            newName = "displayName",
            preview = true,
        )

        assertTrue(result.changes.isNotEmpty(), "Should have at least one change")
        val change = result.changes.first { it.filePath.endsWith("UserProfile.kt") }
        assertTrue(change.after.contains("profile.displayName"), "Property access should be renamed. Content:\n${change.after}")
        assertTrue(!change.after.contains("profile.fullName"), "Old property access should be gone. Content:\n${change.after}")
    }

    @Test
    fun `renames named argument at constructor call site`() {
        val result = RenamePropertyRewriter.rename(
            sourceRoots = listOf(testProjectSrc),
            className = "com.example.variants.property.UserProfile",
            propertyName = "fullName",
            newName = "displayName",
            preview = true,
        )

        assertTrue(result.changes.isNotEmpty(), "Should have at least one change")
        val change = result.changes.first { it.filePath.endsWith("UserProfile.kt") }
        assertTrue(change.after.contains("displayName = name"), "Named argument at constructor call site should be renamed. Content:\n${change.after}")
        assertTrue(!change.after.contains("fullName = name"), "Old named argument should be gone from constructor call. Content:\n${change.after}")
    }

    @Test
    fun `renames named argument in copy call`() {
        val result = RenamePropertyRewriter.rename(
            sourceRoots = listOf(testProjectSrc),
            className = "com.example.variants.property.UserProfile",
            propertyName = "fullName",
            newName = "displayName",
            preview = true,
        )

        assertTrue(result.changes.isNotEmpty(), "Should have at least one change")
        val change = result.changes.first { it.filePath.endsWith("UserProfile.kt") }
        assertTrue(change.after.contains("copy(displayName = newName)"), "Named argument in copy() call should be renamed. Content:\n${change.after}")
        assertTrue(!change.after.contains("copy(fullName = newName)"), "Old named argument should be gone from copy() call. Content:\n${change.after}")
    }

    @Test
    fun `returns change with before and after content`() {
        val result = RenamePropertyRewriter.rename(
            sourceRoots = listOf(testProjectSrc),
            className = "com.example.variants.property.UserProfile",
            propertyName = "fullName",
            newName = "displayName",
            preview = true,
        )

        assertTrue(result.changes.isNotEmpty())
        val change = result.changes.first()
        assertTrue(change.before.contains("fullName"))
        assertTrue(change.after.contains("displayName"))
        assertTrue(change.filePath.endsWith("UserProfile.kt"))
    }

    @Test
    fun `leaves unrelated files unchanged`() {
        val result = RenamePropertyRewriter.rename(
            sourceRoots = listOf(testProjectSrc),
            className = "com.example.variants.property.UserProfile",
            propertyName = "fullName",
            newName = "displayName",
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
        val sourceDir = copySourcesToTemp("rename-property-preview", "com/example/variants/property")
        val profileFile = File(sourceDir, "com/example/variants/property/UserProfile.kt")
        val originalContent = profileFile.readText()

        val result = RenamePropertyRewriter.rename(
            sourceRoots = listOf(sourceDir),
            className = "com.example.variants.property.UserProfile",
            propertyName = "fullName",
            newName = "displayName",
            preview = true,
        )

        assertTrue(result.changes.isNotEmpty(), "Should detect changes")
        assertEquals(originalContent, profileFile.readText(), "File should not be modified in preview mode")
    }

    @Test
    fun `renames property on existing constructorparam fixture`() {
        val result = RenamePropertyRewriter.rename(
            sourceRoots = listOf(testProjectSrc),
            className = "com.example.variants.constructorparam.Registration",
            propertyName = "fullName",
            newName = "displayName",
            preview = true,
        )

        assertTrue(result.changes.isNotEmpty(), "Should have changes for constructorparam fixture")
        val change = result.changes.first { it.filePath.endsWith("constructorparam/Registration.kt") }
        assertTrue(change.after.contains("val displayName: String"), "Constructor val should be renamed. Content:\n${change.after}")
        assertTrue(change.after.contains("displayName = fullName"), "Named argument at call site should be renamed. Content:\n${change.after}")
    }

    @Test
    fun `RenamePropertyResult JSON roundtrip preserves empty changes`() {
        val result = RenamePropertyResult(emptyList())

        val json = result.toJson()
        val deserialized = RenamePropertyResult.fromJson(json)

        assertEquals(result, deserialized)
    }

    @Test
    fun `RenamePropertyResult JSON roundtrip preserves changes with special characters`() {
        val result = RenamePropertyResult(listOf(
            RenameChange(
                filePath = "/path/to/File.kt",
                before = """val x = "hello \"world\"" """,
                after = """val y = "hello \"world\"" """,
            ),
        ))

        val json = result.toJson()
        val deserialized = RenamePropertyResult.fromJson(json)

        assertEquals(result, deserialized)
    }

    @Test
    fun `RenamePropertyResult JSON roundtrip preserves multi-line source code`() {
        val result = RenamePropertyResult(listOf(
            RenameChange(
                filePath = "/src/main/kotlin/com/example/Data.kt",
                before = "data class User(val fullName: String)\nfun use() = user.fullName",
                after = "data class User(val displayName: String)\nfun use() = user.displayName",
            ),
            RenameChange(
                filePath = "/src/main/kotlin/com/example/Service.kt",
                before = "User(fullName = name)",
                after = "User(displayName = name)",
            ),
        ))

        val json = result.toJson()
        val deserialized = RenamePropertyResult.fromJson(json)

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
