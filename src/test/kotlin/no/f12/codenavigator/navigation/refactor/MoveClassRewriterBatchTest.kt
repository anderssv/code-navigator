package no.f12.codenavigator.navigation.refactor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import java.io.File
import java.nio.file.Files

class MoveClassRewriterBatchTest {

    private val testProjectSrc = File("test-project/src/main/kotlin")
    private val testProjectClasses = File("test-project/build/classes/kotlin/main").toPath()

    private fun freshTempDir(): File {
        val tempDir = Files.createTempDirectory("cnav-test-moveclass-batch").toFile()
        for (pkg in listOf("com/example/variants/moveclass/original", "com/example/variants/moveclass/consumer")) {
            File(testProjectSrc, pkg).copyRecursively(File(tempDir, pkg))
        }
        return tempDir
    }

    @Test
    fun `empty moves list returns empty results`() {
        val results = MoveClassRewriter.moveBatch(
            sourceRoots = listOf(testProjectSrc),
            moves = emptyList(),
            classpath = listOf(testProjectClasses),
            preview = true,
        )

        assertTrue(results.isEmpty())
    }

    @Test
    fun `batch moves two independent classes to the same package in one pass`() {
        val results = MoveClassRewriter.moveBatch(
            sourceRoots = listOf(testProjectSrc),
            moves = listOf(
                BatchMoveRequest("com.example.variants.moveclass.original.PaymentService", "com.example.variants.moveclass.billing.PaymentService"),
                BatchMoveRequest("com.example.variants.moveclass.original.InventoryService", "com.example.variants.moveclass.billing.InventoryService"),
            ),
            classpath = listOf(testProjectClasses),
            preview = true,
        )

        assertEquals(2, results.size)

        val paymentResult = results[0]
        val paymentChange = paymentResult.changes.firstOrNull { it.filePath.endsWith("PaymentService.kt") }
        assertTrue(paymentChange != null, "PaymentService.kt should be changed. Got: ${paymentResult.changes.map { it.filePath }}")
        assertTrue(paymentChange.after.contains("package com.example.variants.moveclass.billing"))
        assertTrue(!paymentChange.after.contains("<error>"), "Package declaration must not be corrupted. Content:\n${paymentChange.after}")

        val inventoryResult = results[1]
        val inventoryChange = inventoryResult.changes.firstOrNull { it.filePath.endsWith("InventoryService.kt") }
        assertTrue(inventoryChange != null, "InventoryService.kt should be changed. Got: ${inventoryResult.changes.map { it.filePath }}")
        assertTrue(inventoryChange.after.contains("package com.example.variants.moveclass.billing"))
        assertTrue(!inventoryChange.after.contains("<error>"), "Package declaration must not be corrupted. Content:\n${inventoryChange.after}")
    }

    @Test
    fun `batch move updates consumer imports for each moved class independently`() {
        val results = MoveClassRewriter.moveBatch(
            sourceRoots = listOf(testProjectSrc),
            moves = listOf(
                BatchMoveRequest("com.example.variants.moveclass.original.PaymentService", "com.example.variants.moveclass.billing.PaymentService"),
                BatchMoveRequest("com.example.variants.moveclass.original.InventoryService", "com.example.variants.moveclass.billing.InventoryService"),
            ),
            classpath = listOf(testProjectClasses),
            preview = true,
        )

        val paymentResult = results[0]
        val orderConsumerChange = paymentResult.changes.firstOrNull { it.filePath.endsWith("moveclass/consumer/OrderService.kt") }
        assertTrue(orderConsumerChange != null, "consumer/OrderService.kt should be attributed to the PaymentService move. Got: ${paymentResult.changes.map { it.filePath }}")
        assertTrue(orderConsumerChange.after.contains("import com.example.variants.moveclass.billing.PaymentService"))

        // InventoryService's own step should not claim consumer/OrderService.kt (that file doesn't reference InventoryService)
        val inventoryResult = results[1]
        assertTrue(
            inventoryResult.changes.none { it.filePath.endsWith("moveclass/consumer/OrderService.kt") },
            "consumer/OrderService.kt doesn't import InventoryService, should not appear under that step. Got: ${inventoryResult.changes.map { it.filePath }}",
        )
    }

    @Test
    fun `batch move handles classes that reference each other within the same batch`() {
        // original/OrderService.kt references PaymentService, InventoryService, and Notifier — all same-package,
        // implicit references. Moving all four together in one batch must not corrupt any of them.
        val results = MoveClassRewriter.moveBatch(
            sourceRoots = listOf(testProjectSrc),
            moves = listOf(
                BatchMoveRequest("com.example.variants.moveclass.original.OrderService", "com.example.variants.moveclass.billing.OrderService"),
                BatchMoveRequest("com.example.variants.moveclass.original.PaymentService", "com.example.variants.moveclass.billing.PaymentService"),
                BatchMoveRequest("com.example.variants.moveclass.original.InventoryService", "com.example.variants.moveclass.billing.InventoryService"),
                BatchMoveRequest("com.example.variants.moveclass.original.Notifier", "com.example.variants.moveclass.billing.Notifier"),
            ),
            classpath = listOf(testProjectClasses),
            preview = true,
        )

        assertEquals(4, results.size)
        for ((i, result) in results.withIndex()) {
            for (change in result.changes) {
                assertTrue(!change.after.contains("<error>"), "Step $i produced a corrupted file ${change.filePath}:\n${change.after}")
            }
        }

        val orderChange = results[0].changes.firstOrNull { it.filePath.endsWith("original/OrderService.kt") }
        assertTrue(orderChange != null, "OrderService.kt should be changed. Got: ${results[0].changes.map { it.filePath }}")
        assertTrue(orderChange.after.contains("package com.example.variants.moveclass.billing"), "Content:\n${orderChange.after}")
        assertTrue(orderChange.after.contains("class OrderService"), "Class declaration must survive the batch. Content:\n${orderChange.after}")
        assertTrue(orderChange.after.contains("PaymentService") && orderChange.after.contains("InventoryService") && orderChange.after.contains("Notifier"), "References to the co-moved classes must survive. Content:\n${orderChange.after}")
    }

    @Test
    fun `batch move does not import co-moving siblings from the package they are leaving`() {
        // Regression test: OrderService references PaymentService/InventoryService/Notifier via
        // implicit same-package access (no import today, since all four are in `original`). When
        // all four move together, the sibling-import-adding step must not point at `original` for
        // classes that are ALSO leaving `original` in this same batch — that package won't contain
        // them anymore once their own move step applies. Caught live via a scratch-project run
        // where cnavMovePackage produced `import com.example.old.Payment` after Payment had
        // already moved to `com.example.billing` in the same batch.
        val results = MoveClassRewriter.moveBatch(
            sourceRoots = listOf(testProjectSrc),
            moves = listOf(
                BatchMoveRequest("com.example.variants.moveclass.original.OrderService", "com.example.variants.moveclass.billing.OrderService"),
                BatchMoveRequest("com.example.variants.moveclass.original.PaymentService", "com.example.variants.moveclass.billing.PaymentService"),
                BatchMoveRequest("com.example.variants.moveclass.original.InventoryService", "com.example.variants.moveclass.billing.InventoryService"),
                BatchMoveRequest("com.example.variants.moveclass.original.Notifier", "com.example.variants.moveclass.billing.Notifier"),
            ),
            classpath = listOf(testProjectClasses),
            preview = true,
        )

        val orderChange = results[0].changes.firstOrNull { it.filePath.endsWith("original/OrderService.kt") }
        assertTrue(orderChange != null, "OrderService.kt should be changed. Got: ${results[0].changes.map { it.filePath }}")
        assertTrue(
            !orderChange.after.contains("import com.example.variants.moveclass.original."),
            "Must not import co-moving siblings from the package they're leaving. Content:\n${orderChange.after}",
        )
        // All three are moving to the SAME package as OrderService itself, so they stay implicit — no import needed at all.
        assertTrue(
            !orderChange.after.contains("import com.example.variants.moveclass.billing.PaymentService") &&
                !orderChange.after.contains("import com.example.variants.moveclass.billing.InventoryService") &&
                !orderChange.after.contains("import com.example.variants.moveclass.billing.Notifier"),
            "Co-moving siblings landing in the same package need no import at all. Content:\n${orderChange.after}",
        )
    }

    @Test
    fun `batch move imports a co-moving sibling from its own new package when targets differ`() {
        // PaymentService and OrderService both start in `original`, but here they move to DIFFERENT
        // target packages. OrderService's implicit reference to PaymentService must become an
        // import pointing at PaymentService's own new package, not OrderService's.
        val results = MoveClassRewriter.moveBatch(
            sourceRoots = listOf(testProjectSrc),
            moves = listOf(
                BatchMoveRequest("com.example.variants.moveclass.original.OrderService", "com.example.variants.moveclass.billing.OrderService"),
                BatchMoveRequest("com.example.variants.moveclass.original.PaymentService", "com.example.variants.moveclass.payments.PaymentService"),
                BatchMoveRequest("com.example.variants.moveclass.original.InventoryService", "com.example.variants.moveclass.billing.InventoryService"),
                BatchMoveRequest("com.example.variants.moveclass.original.Notifier", "com.example.variants.moveclass.billing.Notifier"),
            ),
            classpath = listOf(testProjectClasses),
            preview = true,
        )

        val orderChange = results[0].changes.firstOrNull { it.filePath.endsWith("original/OrderService.kt") }
        assertTrue(orderChange != null, "OrderService.kt should be changed. Got: ${results[0].changes.map { it.filePath }}")
        assertTrue(
            orderChange.after.contains("import com.example.variants.moveclass.payments.PaymentService"),
            "PaymentService moved to a different package than OrderService, so OrderService needs an explicit import pointing there. Content:\n${orderChange.after}",
        )
        assertTrue(
            !orderChange.after.contains("import com.example.variants.moveclass.original.PaymentService"),
            "Must not import from the package PaymentService is leaving. Content:\n${orderChange.after}",
        )
    }

    @Test
    fun `batch move with allClasses over one file falls back to per-class multi-class handling`() {
        // EventProcessor lives in Events.kt together with Event — a genuine multi-class file.
        // Requesting only EventProcessor should hit the existing sibling-error path, not the batch path.
        val results = MoveClassRewriter.moveBatch(
            sourceRoots = listOf(testProjectSrc),
            moves = listOf(
                BatchMoveRequest("com.example.variants.moveclass.original.EventProcessor", "com.example.variants.moveclass.events.EventProcessor"),
            ),
            classpath = listOf(testProjectClasses),
            preview = true,
        )

        assertEquals(1, results.size)
        assertTrue(results[0].error != null, "Should return the existing sibling error. Got: ${results[0]}")
        assertTrue(results[0].error!!.contains("Event"))
        assertTrue(results[0].changes.isEmpty())
    }

    @Test
    fun `batch move routes Kt facades through the existing single-class path`() {
        val results = MoveClassRewriter.moveBatch(
            sourceRoots = listOf(testProjectSrc),
            moves = listOf(
                BatchMoveRequest("com.example.variants.moveclass.original.CookieSupportKt", "com.example.variants.moveclass.http.CookieSupportKt"),
            ),
            classpath = listOf(testProjectClasses),
            preview = true,
        )

        assertEquals(1, results.size)
        val cookieChange = results[0].changes.firstOrNull { it.filePath.endsWith("CookieSupport.kt") }
        assertTrue(cookieChange != null, "Should have a change for CookieSupport.kt. Got: ${results[0].changes.map { it.filePath }}")
        assertTrue(cookieChange.after.contains("package com.example.variants.moveclass.http"))
    }

    @Test
    fun `batch move class not found produces empty result for that step only`() {
        val results = MoveClassRewriter.moveBatch(
            sourceRoots = listOf(testProjectSrc),
            moves = listOf(
                BatchMoveRequest("com.example.variants.moveclass.original.NonExistentClass", "com.example.variants.moveclass.billing.NonExistentClass"),
                BatchMoveRequest("com.example.variants.moveclass.original.PaymentService", "com.example.variants.moveclass.billing.PaymentService"),
            ),
            classpath = listOf(testProjectClasses),
            preview = true,
        )

        assertEquals(2, results.size)
        assertTrue(results[0].changes.isEmpty())
        assertTrue(results[1].changes.isNotEmpty())
    }

    @Test
    fun `non-preview batch move writes files and renames them on disk`() {
        val tempDir = freshTempDir()

        val results = MoveClassRewriter.moveBatch(
            sourceRoots = listOf(tempDir),
            moves = listOf(
                BatchMoveRequest("com.example.variants.moveclass.original.PaymentService", "com.example.variants.moveclass.billing.PaymentService"),
                BatchMoveRequest("com.example.variants.moveclass.original.InventoryService", "com.example.variants.moveclass.billing.InventoryService"),
            ),
            classpath = listOf(testProjectClasses),
            preview = false,
        )

        assertEquals(2, results.size)
        for (result in results) {
            assertTrue(result.movedFilePath != null)
            assertTrue(result.newFilePath != null)
            val newFile = File(result.newFilePath!!)
            assertTrue(newFile.exists(), "New file should exist: ${result.newFilePath}")
            val oldFile = File(result.movedFilePath!!)
            assertTrue(!oldFile.exists(), "Old file should be gone: ${result.movedFilePath}")
        }

        val orderFile = File(tempDir, "com/example/variants/moveclass/consumer/OrderService.kt")
        val orderContent = orderFile.readText()
        assertTrue(orderContent.contains("import com.example.variants.moveclass.billing.PaymentService"), "Consumer import should be updated on disk. Content:\n$orderContent")

        tempDir.deleteRecursively()
    }

    @Test
    fun `preview batch move does not write to disk`() {
        val originalContent = File(testProjectSrc, "com/example/variants/moveclass/original/PaymentService.kt").readText()

        MoveClassRewriter.moveBatch(
            sourceRoots = listOf(testProjectSrc),
            moves = listOf(
                BatchMoveRequest("com.example.variants.moveclass.original.PaymentService", "com.example.variants.moveclass.billing.PaymentService"),
            ),
            classpath = listOf(testProjectClasses),
            preview = true,
        )

        assertEquals(originalContent, File(testProjectSrc, "com/example/variants/moveclass/original/PaymentService.kt").readText())
    }
}
