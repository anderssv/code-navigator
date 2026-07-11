package no.f12.codenavigator.navigation.refactor

import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue

/** The rewriter only edits .kt files; a .java consumer of a moved Kotlin class must be flagged so an agent fixes it independently. */
class MoveClassJavaWarningTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `warns about a Java file that references the moved class`() {
        val root = tempDir.toFile()
        File(root, "com/app").mkdirs()
        File(root, "com/app/Widget.kt").writeText("package com.app\n\nclass Widget\n")
        File(root, "com/app/JavaConsumer.java").writeText(
            "package com.app;\n\npublic class JavaConsumer {\n    com.app.Widget w;\n}\n",
        )

        val result = MoveClassRewriter.move(
            sourceRoots = listOf(root),
            className = "com.app.Widget",
            newFqcn = "com.app.moved.Widget",
            preview = true,
        )

        assertTrue(result.warnings.any { it.contains("non-Kotlin") && it.contains("JavaConsumer.java") },
            "Expected a non-Kotlin reference warning naming JavaConsumer.java. Warnings: ${result.warnings}")
    }

    @Test
    fun `no warning when no non-Kotlin file references the class`() {
        val root = tempDir.toFile()
        File(root, "com/app").mkdirs()
        File(root, "com/app/Widget.kt").writeText("package com.app\n\nclass Widget\n")
        File(root, "com/app/Other.java").writeText("package com.app;\n\npublic class Other {}\n")

        val result = MoveClassRewriter.move(
            sourceRoots = listOf(root),
            className = "com.app.Widget",
            newFqcn = "com.app.moved.Widget",
            preview = true,
        )

        assertTrue(result.warnings.none { it.contains("non-Kotlin") },
            "Should not warn — Other.java does not reference Widget. Warnings: ${result.warnings}")
    }
}
