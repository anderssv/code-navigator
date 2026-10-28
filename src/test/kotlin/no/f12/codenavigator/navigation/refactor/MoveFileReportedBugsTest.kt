package no.f12.codenavigator.navigation.refactor

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Regressions from a real run of cnavMoveFile (0.1.120) that moved the wrong file, added hundreds of
 * imports for declarations a file doesn't use, imported private declarations, and left the moved file
 * without imports for what it used from its old package. Fixtures: test-project .../variants/movefile.
 */
class MoveFileReportedBugsTest {

    private val src = File("test-project/src/main/kotlin")
    private val classes = File("test-project/build/classes/kotlin/main").toPath()
    private val base = "com/example/variants/movefile"
    private val pkg = "com.example.variants.movefile"

    private fun move(file: String, toPackage: String) = MoveFileRewriter.move(
        sourceRoots = listOf(src),
        fromFile = "$base/$file",
        toPackage = toPackage,
        classpath = listOf(classes),
        preview = true,
    )

    private fun MoveClassResult.change(fileName: String) = changes.firstOrNull { it.filePath.endsWith("/$fileName") }

    // --- 1. the right file ---------------------------------------------------------------------------

    @Test
    fun `moves the file that was asked for, not another file declaring a nested class with the same short name`() {
        val result = move("users/NotAvailable.kt", "$pkg.users.web")

        assertTrue(result.error == null, "unexpected error: ${result.error}")
        assertTrue(result.movedFilePath!!.endsWith("users/NotAvailable.kt"), "moved: ${result.movedFilePath}")
        assertTrue(result.newFilePath!!.endsWith("users/web/NotAvailable.kt"), "new path: ${result.newFilePath}")
        assertEquals(null, result.change("BankIdLookup.kt"), "a file in a subpackage must not be touched: ${result.changes.map { it.filePath }}")
        assertTrue(result.change("NotAvailable.kt")!!.after.contains("package $pkg.users.web\n"))
    }

    @Test
    fun `a file in a subpackage never has its package line rewritten as if it were in the old package`() {
        val result = move("users/Texts.kt", "$pkg.users.web")

        for (name in listOf("BankIdLookup.kt", "Model.kt")) {
            assertEquals(null, result.change(name)?.after?.takeIf { it.contains("users.web.ods") || it.contains("users.web.model") }, "$name was rewritten")
        }
    }

    // --- 2. imports only where the moved declaration is used --------------------------------------------

    @Test
    fun `extensions on a nullable receiver do not make the receiver type a moved declaration`() {
        val result = move("users/Texts.kt", "$pkg.users.web")

        assertEquals(null, result.change("Model.kt"), "Model.kt declares Actor/Reason itself and must not import them from users.web")
    }

    // --- 3. never import private declarations --------------------------------------------------------

    @Test
    fun `a private top-level function in the moved file is never imported anywhere`() {
        val result = move("users/Texts.kt", "$pkg.users.web")

        assertEquals(null, result.change("PrivateUser.kt"), "PrivateUser only has a property named like the private function")
        assertTrue(result.changes.none { it.after.contains("users.web.orderId") }, "imported a private function")
    }

    // --- 4. the moved file keeps what it used from its old package ----------------------------------------

    @Test
    fun `the moved file gets imports for what it used from its old package`() {
        val result = move("web/Server.kt", "$pkg.server")

        val moved = result.change("Server.kt")!!.after
        assertTrue(moved.contains("package $pkg.server"), moved)
        for (name in listOf("NotFoundException", "dataSourceRoutes", "DEFAULT_ROUTE")) {
            assertTrue(moved.contains("import $pkg.web.$name"), "missing import of $name in:\n$moved")
        }
    }
}
