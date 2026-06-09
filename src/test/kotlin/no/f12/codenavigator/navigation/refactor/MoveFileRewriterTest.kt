package no.f12.codenavigator.navigation.refactor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import java.io.File

class MoveFileRewriterTest {

    private val testProjectSrc = File("test-project/src/main/kotlin")
    private val testProjectClasses = File("test-project/build/classes/kotlin/main").toPath()

    @Test
    fun `move file updates all classes and top-level declarations in the file`() {
        val result = MoveFileRewriter.move(
            sourceRoots = listOf(testProjectSrc),
            fromFile = "com/example/variants/moveclass/original/Events.kt",
            toPackage = "com.example.variants.moveclass.events",
            classpath = listOf(testProjectClasses),
            preview = true,
        )

        assertTrue(result.movedFilePath != null, "Should find and move the file. movedFilePath: ${result.movedFilePath}")
        assertTrue(result.movedFilePath!!.endsWith("Events.kt"), "movedFilePath should be Events.kt")

        val consumerChange = result.changes.firstOrNull { it.filePath.endsWith("EventConsumer.kt") }
        assertTrue(consumerChange != null, "Should update EventConsumer.kt")
        assertTrue(
            consumerChange!!.after.contains("import com.example.variants.moveclass.events.Event"),
            "Should update Event import",
        )
        assertTrue(
            consumerChange.after.contains("import com.example.variants.moveclass.events.EventProcessor"),
            "Should update EventProcessor import",
        )
    }

    @Test
    fun `move file with non-existent path returns no changes`() {
        val result = MoveFileRewriter.move(
            sourceRoots = listOf(testProjectSrc),
            fromFile = "does/not/exist/Foo.kt",
            toPackage = "com.example.new",
            classpath = listOf(testProjectClasses),
            preview = true,
        )

        assertTrue(result.changes.isEmpty())
    }

    @Test
    fun `move file to same package returns no changes`() {
        val result = MoveFileRewriter.move(
            sourceRoots = listOf(testProjectSrc),
            fromFile = "com/example/variants/moveclass/original/Events.kt",
            toPackage = "com.example.variants.moveclass.original",
            classpath = listOf(testProjectClasses),
            preview = true,
        )

        assertTrue(result.changes.isEmpty())
    }

    @Test
    fun `move file with extension functions does not rewrite imports of receiver type in other files`() {
        // ExtensionFunctions.kt contains extension functions on ExtensionHost (e.g. fun ExtensionHost.doubled()).
        // ExtensionHostUser.kt imports ExtensionHost but NOT the extension functions.
        // Moving ExtensionFunctions.kt must NOT rewrite the ExtensionHost import in ExtensionHostUser.kt.
        val result = MoveFileRewriter.move(
            sourceRoots = listOf(testProjectSrc),
            fromFile = "com/example/variants/moveclass/original/ExtensionFunctions.kt",
            toPackage = "com.example.variants.moveclass.extensions",
            classpath = listOf(testProjectClasses),
            preview = true,
        )

        assertTrue(result.movedFilePath != null, "Should find and move the file")

        val hostUserChange = result.changes.firstOrNull { it.filePath.endsWith("ExtensionHostUser.kt") }
        assertTrue(
            hostUserChange == null || hostUserChange.after.contains("import com.example.variants.moveclass.original.ExtensionHost"),
            "Should NOT rewrite ExtensionHost import in ExtensionHostUser.kt — ExtensionHost did not move",
        )
    }
}
