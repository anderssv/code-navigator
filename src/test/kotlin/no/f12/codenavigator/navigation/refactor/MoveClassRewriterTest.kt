package no.f12.codenavigator.navigation.refactor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import java.io.File
import java.nio.file.Files

class MoveClassRewriterTest {

    private val testProjectSrc = File("test-project/src/main/kotlin")
    private val testProjectClasses = File("test-project/build/classes/kotlin/main").toPath()

    companion object {
        private val cachedParsedSources by lazy {
            parseKotlinSources(
                listOf(File("test-project/src/main/kotlin")),
                classpath = listOf(File("test-project/build/classes/kotlin/main").toPath()),
            )
        }
    }

    @Test
    fun `empty source roots returns no changes`() {
        val result = MoveClassRewriter.move(
            sourceRoots = emptyList(),
            className = "com.example.Foo",
            newFqcn = "com.example.bar.Foo",
            preview = true,
        )

        assertTrue(result.changes.isEmpty())
    }

    @Test
    fun `class not found returns no changes`() {
        val result = MoveClassRewriter.move(
            sourceRoots = listOf(testProjectSrc),
            className = "com.example.NonExistentClass",
            newFqcn = "com.example.newpkg.NonExistentClass",
            classpath = listOf(testProjectClasses),
            preview = true,
            parsedSources = cachedParsedSources,
        )

        assertTrue(result.changes.isEmpty())
    }

    @Test
    fun `updates package declaration of moved class`() {
        val result = MoveClassRewriter.move(
            sourceRoots = listOf(testProjectSrc),
            className = "com.example.variants.moveclass.original.PaymentService",
            newFqcn = "com.example.variants.moveclass.billing.PaymentService",
            classpath = listOf(testProjectClasses),
            preview = true,
            parsedSources = cachedParsedSources,
        )

        assertTrue(result.changes.isNotEmpty(), "Should detect changes. Result has ${result.changes.size} changes. Moved: ${result.movedFilePath}")
        val changedFiles = result.changes.map { it.filePath }
        val paymentChange = result.changes.firstOrNull { it.filePath.endsWith("PaymentService.kt") }
        assertTrue(paymentChange != null, "Should have a change for PaymentService.kt. Changed files: $changedFiles")
        assertTrue(paymentChange.after.contains("package com.example.variants.moveclass.billing"), "Package declaration should be updated. Content:\n${paymentChange.after}")
        assertTrue(!paymentChange.after.contains("package com.example.variants.moveclass.original"), "Old package declaration should be gone. Content:\n${paymentChange.after}")
    }

    @Test
    fun `updates import in a file that imports the moved class`() {
        val result = MoveClassRewriter.move(
            sourceRoots = listOf(testProjectSrc),
            className = "com.example.variants.moveclass.original.PaymentService",
            newFqcn = "com.example.variants.moveclass.billing.PaymentService",
            classpath = listOf(testProjectClasses),
            preview = true,
            parsedSources = cachedParsedSources,
        )

        val orderChange = result.changes.firstOrNull { it.filePath.endsWith("moveclass/consumer/OrderService.kt") }
        assertTrue(orderChange != null, "Should have a change for OrderService.kt. Changed files: ${result.changes.map { it.filePath }}")
        assertTrue(orderChange.after.contains("import com.example.variants.moveclass.billing.PaymentService"), "Import should be updated. Content:\n${orderChange.after}")
        assertTrue(!orderChange.after.contains("import com.example.variants.moveclass.original.PaymentService"), "Old import should be gone. Content:\n${orderChange.after}")
    }

    @Test
    fun `updates multiple files that import the moved class`() {
        val result = MoveClassRewriter.move(
            sourceRoots = listOf(testProjectSrc),
            className = "com.example.variants.moveclass.original.PaymentService",
            newFqcn = "com.example.variants.moveclass.billing.PaymentService",
            classpath = listOf(testProjectClasses),
            preview = true,
            parsedSources = cachedParsedSources,
        )

        val orderChange = result.changes.firstOrNull { it.filePath.endsWith("moveclass/consumer/OrderService.kt") }
        val reportChange = result.changes.firstOrNull { it.filePath.endsWith("moveclass/consumer/ReportService.kt") }

        assertTrue(orderChange != null, "OrderService should be updated")
        assertTrue(reportChange != null, "ReportService should be updated")
        assertTrue(orderChange.after.contains("import com.example.variants.moveclass.billing.PaymentService"))
        assertTrue(reportChange.after.contains("import com.example.variants.moveclass.billing.PaymentService"))
    }

    @Test
    fun `does not modify files that dont reference the moved class`() {
        val result = MoveClassRewriter.move(
            sourceRoots = listOf(testProjectSrc),
            className = "com.example.variants.moveclass.original.PaymentService",
            newFqcn = "com.example.variants.moveclass.billing.PaymentService",
            classpath = listOf(testProjectClasses),
            preview = true,
            parsedSources = cachedParsedSources,
        )

        val shippingChange = result.changes.firstOrNull { it.filePath.endsWith("ShippingService.kt") }
        assertTrue(shippingChange == null, "ShippingService should NOT be changed. Changed files: ${result.changes.map { it.filePath }}")
    }

    @Test
    fun `preview mode does not write to disk or move files`() {
        val sourceDir = copySourcesToTemp("moveclass-preview",
            "com/example/variants/moveclass/original",
            "com/example/variants/moveclass/consumer",
        )
        val paymentFile = File(sourceDir, "com/example/variants/moveclass/original/PaymentService.kt")
        val originalContent = paymentFile.readText()

        val result = MoveClassRewriter.move(
            sourceRoots = listOf(sourceDir),
            className = "com.example.variants.moveclass.original.PaymentService",
            newFqcn = "com.example.variants.moveclass.billing.PaymentService",
            classpath = listOf(testProjectClasses),
            preview = true,
        )

        assertTrue(result.changes.isNotEmpty(), "Should detect changes")
        assertEquals(originalContent, paymentFile.readText(), "File should not be modified in preview mode")
        assertTrue(paymentFile.exists(), "Original file should still exist in preview mode")
    }

    @Test
    fun `non-preview mode moves the file to new package directory`() {
        val sourceDir = copySourcesToTemp("moveclass-apply",
            "com/example/variants/moveclass/original",
            "com/example/variants/moveclass/consumer",
        )

        val result = MoveClassRewriter.move(
            sourceRoots = listOf(sourceDir),
            className = "com.example.variants.moveclass.original.PaymentService",
            newFqcn = "com.example.variants.moveclass.billing.PaymentService",
            classpath = listOf(testProjectClasses),
            preview = false,
        )

        assertTrue(result.changes.isNotEmpty(), "Should detect changes")
        assertTrue(result.movedFilePath != null, "Should report moved file path")
        assertTrue(result.newFilePath != null, "Should report new file path")

        val newFile = File(result.newFilePath!!)
        assertTrue(newFile.exists(), "File should exist at new location: ${result.newFilePath}")
        assertTrue(newFile.readText().contains("package com.example.variants.moveclass.billing"), "New file should have updated package")

        val oldFile = File(result.movedFilePath!!)
        assertTrue(!oldFile.exists(), "Old file should no longer exist at: ${result.movedFilePath}")
    }

    @Test
    fun `non-preview mode updates import in consumer files on disk`() {
        val sourceDir = copySourcesToTemp("moveclass-apply-imports",
            "com/example/variants/moveclass/original",
            "com/example/variants/moveclass/consumer",
        )

        MoveClassRewriter.move(
            sourceRoots = listOf(sourceDir),
            className = "com.example.variants.moveclass.original.PaymentService",
            newFqcn = "com.example.variants.moveclass.billing.PaymentService",
            classpath = listOf(testProjectClasses),
            preview = false,
        )

        val orderFile = File(sourceDir, "com/example/variants/moveclass/consumer/OrderService.kt")
        val orderContent = orderFile.readText()
        assertTrue(orderContent.contains("import com.example.variants.moveclass.billing.PaymentService"), "Import should be updated on disk. Content:\n$orderContent")
    }

    @Test
    fun `moves an interface file to new package directory`() {
        val sourceDir = copySourcesToTemp("moveclass-interface",
            "com/example/variants/moveclass/original",
            "com/example/variants/moveclass/consumer",
        )

        val result = MoveClassRewriter.move(
            sourceRoots = listOf(sourceDir),
            className = "com.example.variants.moveclass.original.Notifier",
            newFqcn = "com.example.variants.moveclass.events.Notifier",
            classpath = listOf(testProjectClasses),
            preview = false,
        )

        assertTrue(result.changes.isNotEmpty(), "Should detect changes")
        assertTrue(result.movedFilePath != null, "Should report moved file path")
        assertTrue(result.newFilePath != null, "Should report new file path")

        val newFile = File(result.newFilePath!!)
        assertTrue(newFile.exists(), "Interface file should exist at new location: ${result.newFilePath}")
        assertTrue(newFile.readText().contains("package com.example.variants.moveclass.events"), "New file should have updated package")

        val oldFile = File(result.movedFilePath!!)
        assertTrue(!oldFile.exists(), "Old interface file should no longer exist at: ${result.movedFilePath}")
    }

    @Test
    fun `rename class in same package updates class declaration`() {
        val result = MoveClassRewriter.move(
            sourceRoots = listOf(testProjectSrc),
            className = "com.example.variants.moveclass.original.PaymentService",
            newFqcn = "com.example.variants.moveclass.original.BillingService",
            classpath = listOf(testProjectClasses),
            preview = true,
            parsedSources = cachedParsedSources,
        )

        assertTrue(result.changes.isNotEmpty(), "Should detect changes")
        val paymentChange = result.changes.firstOrNull { it.filePath.endsWith("PaymentService.kt") }
        assertTrue(paymentChange != null, "Should have a change for PaymentService.kt. Changed files: ${result.changes.map { it.filePath }}")
        assertTrue(paymentChange.after.contains("class BillingService"), "Class declaration should be renamed. Content:\n${paymentChange.after}")
        assertTrue(!paymentChange.after.contains("class PaymentService"), "Old class name should be gone. Content:\n${paymentChange.after}")
        assertTrue(paymentChange.after.contains("package com.example.variants.moveclass.original"), "Package should remain unchanged. Content:\n${paymentChange.after}")
    }

    @Test
    fun `rename class in same package updates import in consumer files`() {
        val result = MoveClassRewriter.move(
            sourceRoots = listOf(testProjectSrc),
            className = "com.example.variants.moveclass.original.PaymentService",
            newFqcn = "com.example.variants.moveclass.original.BillingService",
            classpath = listOf(testProjectClasses),
            preview = true,
            parsedSources = cachedParsedSources,
        )

        val orderChange = result.changes.firstOrNull { it.filePath.endsWith("OrderService.kt") }
        assertTrue(orderChange != null, "Should have a change for OrderService.kt. Changed files: ${result.changes.map { it.filePath }}")
        assertTrue(orderChange.after.contains("import com.example.variants.moveclass.original.BillingService"), "Import should use new class name. Content:\n${orderChange.after}")
        assertTrue(!orderChange.after.contains("PaymentService"), "Old class name should be gone. Content:\n${orderChange.after}")
    }

    @Test
    fun `rename class in same package renames file on disk`() {
        val sourceDir = copySourcesToTemp("rename-class-apply",
            "com/example/variants/moveclass/original",
            "com/example/variants/moveclass/consumer",
        )

        val result = MoveClassRewriter.move(
            sourceRoots = listOf(sourceDir),
            className = "com.example.variants.moveclass.original.PaymentService",
            newFqcn = "com.example.variants.moveclass.original.BillingService",
            classpath = listOf(testProjectClasses),
            preview = false,
        )

        assertTrue(result.newFilePath != null, "Should report new file path")
        val newFile = File(result.newFilePath!!)
        assertTrue(newFile.exists(), "New file should exist: ${result.newFilePath}")
        assertTrue(newFile.name == "BillingService.kt", "File should be renamed. Actual: ${newFile.name}")
        assertTrue(newFile.readText().contains("class BillingService"), "File content should have new class name")

        val oldFile = File(result.movedFilePath!!)
        assertTrue(!oldFile.exists(), "Old file should no longer exist: ${result.movedFilePath}")
    }

    @Test
    fun `move and rename updates package declaration and class name`() {
        val result = MoveClassRewriter.move(
            sourceRoots = listOf(testProjectSrc),
            className = "com.example.variants.moveclass.original.PaymentService",
            newFqcn = "com.example.variants.moveclass.billing.BillingService",
            classpath = listOf(testProjectClasses),
            preview = true,
            parsedSources = cachedParsedSources,
        )

        assertTrue(result.changes.isNotEmpty(), "Should detect changes")
        val paymentChange = result.changes.firstOrNull { it.filePath.endsWith("PaymentService.kt") }
        assertTrue(paymentChange != null, "Should have a change for PaymentService.kt")
        assertTrue(paymentChange.after.contains("package com.example.variants.moveclass.billing"), "Package should be updated. Content:\n${paymentChange.after}")
        assertTrue(paymentChange.after.contains("class BillingService"), "Class should be renamed. Content:\n${paymentChange.after}")
        assertTrue(!paymentChange.after.contains("class PaymentService"), "Old class name should be gone. Content:\n${paymentChange.after}")

        val orderChange = result.changes.firstOrNull { it.filePath.endsWith("OrderService.kt") }
        assertTrue(orderChange != null, "Consumer should be updated")
        assertTrue(orderChange.after.contains("import com.example.variants.moveclass.billing.BillingService"), "Import should have new package + new name. Content:\n${orderChange.after}")
    }

    @Test
    fun `move without rename preserves class name`() {
        val result = MoveClassRewriter.move(
            sourceRoots = listOf(testProjectSrc),
            className = "com.example.variants.moveclass.original.PaymentService",
            newFqcn = "com.example.variants.moveclass.billing.PaymentService",
            classpath = listOf(testProjectClasses),
            preview = true,
            parsedSources = cachedParsedSources,
        )

        assertTrue(result.changes.isNotEmpty(), "Should detect changes")
        val paymentChange = result.changes.firstOrNull { it.filePath.endsWith("PaymentService.kt") }
        assertTrue(paymentChange != null, "Should have a change for PaymentService.kt")
        assertTrue(paymentChange.after.contains("class PaymentService"), "Class name should be preserved. Content:\n${paymentChange.after}")
        assertTrue(paymentChange.after.contains("package com.example.variants.moveclass.billing"), "Package should be updated. Content:\n${paymentChange.after}")
    }

    @Test
    fun `MoveClassResult JSON roundtrip preserves empty changes`() {
        val result = MoveClassResult(emptyList())

        val json = result.toJson()
        val deserialized = MoveClassResult.fromJson(json)

        assertEquals(result.changes, deserialized.changes)
    }

    @Test
    fun `MoveClassResult JSON roundtrip preserves changes with paths`() {
        val result = MoveClassResult(
            changes = listOf(
                RenameChange(
                    filePath = "/path/to/File.kt",
                    before = "package com.old",
                    after = "package com.new",
                ),
            ),
            movedFilePath = "/path/to/old/File.kt",
            newFilePath = "/path/to/new/File.kt",
        )

        val json = result.toJson()
        val deserialized = MoveClassResult.fromJson(json)

        assertEquals(result.changes, deserialized.changes)
        assertEquals(result.movedFilePath, deserialized.movedFilePath)
        assertEquals(result.newFilePath, deserialized.newFilePath)
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
