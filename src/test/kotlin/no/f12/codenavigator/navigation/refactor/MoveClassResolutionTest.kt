package no.f12.codenavigator.navigation.refactor

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Proves the rename pass is resolution-backed (K1 BindingContext), not just heuristic text matching.
 * A same-package enum entry `Kind.Widget` shares the moved class's simple name; a heuristic word-boundary
 * rename would wrongly rewrite it, but semantic resolution knows it targets the enum entry, not the class.
 */
class MoveClassResolutionTest {

    private val testProjectSrc = File("test-project/src/main/kotlin")
    private val testProjectClasses = File("test-project/build/classes/kotlin/main").toPath()

    @Test
    fun `rename does not touch a same-named enum entry that is not the moved class`() {
        val result = MoveClassRewriter.move(
            sourceRoots = listOf(testProjectSrc),
            className = "com.example.variants.resolve.Widget",
            newFqcn = "com.example.variants.resolve.Gizmo",
            classpath = listOf(testProjectClasses),
            preview = true,
        )

        val userChange = result.changes.firstOrNull { it.filePath.endsWith("resolve/WidgetUser.kt") }
        assertTrue(userChange != null, "WidgetUser.kt should change (it references the moved class). Changes: ${result.changes.map { it.filePath }}")

        // The type reference and constructor call resolve to the class -> renamed.
        assertTrue(userChange.after.contains("val w: Gizmo = Gizmo()"), "Type + constructor should be renamed. Content:\n${userChange.after}")
        // The enum entry DECLARATION shares the simple name but is a different type (Kind.Widget) -> untouched.
        assertTrue(userChange.after.contains("enum class Kind { Widget, Other }"), "Same-named enum entry declaration must NOT be renamed. Content:\n${userChange.after}")
        // The enum entry REFERENCE resolves to Kind.Widget, not the class -> untouched.
        assertTrue(userChange.after.contains("Kind.Widget"), "Same-named enum entry reference must NOT be renamed. Content:\n${userChange.after}")
        assertTrue(!userChange.after.contains("Gizmo, Other") && !userChange.after.contains("Kind.Gizmo"), "Enum entry was wrongly renamed — resolution not applied. Content:\n${userChange.after}")

        // The declaration file itself is renamed.
        val declChange = result.changes.firstOrNull { it.filePath.endsWith("resolve/Widget.kt") }
        assertTrue(declChange != null && declChange.after.contains("class Gizmo"), "Declaration should be renamed.")
    }

    @Test
    fun `without a classpath the heuristic wrongly renames the enum entry - proving resolution is load-bearing`() {
        // Same rename, but no classpath -> no resolution -> heuristic word-boundary matching, which
        // can't tell Kind.Widget (enum entry) from the Widget class. This documents exactly what
        // resolution buys us; if resolution ever silently stops applying, the test above starts failing
        // and this one keeps passing.
        val result = MoveClassRewriter.move(
            sourceRoots = listOf(testProjectSrc),
            className = "com.example.variants.resolve.Widget",
            newFqcn = "com.example.variants.resolve.Gizmo",
            classpath = emptyList(),
            preview = true,
        )

        val userChange = result.changes.firstOrNull { it.filePath.endsWith("resolve/WidgetUser.kt") }
        assertTrue(userChange != null, "WidgetUser.kt should still change under the heuristic.")
        assertTrue(userChange.after.contains("Kind.Gizmo"), "Heuristic is expected to (wrongly) rename the enum entry. Content:\n${userChange.after}")
    }
}
