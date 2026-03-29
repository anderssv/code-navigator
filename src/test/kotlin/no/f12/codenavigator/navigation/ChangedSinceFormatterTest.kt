package no.f12.codenavigator.navigation

import no.f12.codenavigator.navigation.callgraph.MethodRef
import no.f12.codenavigator.navigation.changedsince.ChangedClassImpact
import no.f12.codenavigator.navigation.changedsince.ChangedSinceFormatter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChangedSinceFormatterTest {

    @Test
    fun `formats single changed class with callers`() {
        val impacts = listOf(
            ChangedClassImpact(
                className = ClassName("com.example.Service"),
                sourceFile = "Service.kt",
                callers = setOf(
                    MethodRef(ClassName("com.example.Controller"), "handle"),
                ),
            ),
        )

        val result = ChangedSinceFormatter.format(impacts, unresolved = emptyList())

        assertTrue(result.contains("com.example.Service"))
        assertTrue(result.contains("com.example.Controller.handle"))
    }

    @Test
    fun `formats changed class with no callers`() {
        val impacts = listOf(
            ChangedClassImpact(
                className = ClassName("com.example.Orphan"),
                sourceFile = "Orphan.kt",
                callers = emptySet(),
            ),
        )

        val result = ChangedSinceFormatter.format(impacts, unresolved = emptyList())

        assertTrue(result.contains("com.example.Orphan"))
        assertTrue(result.contains("(no callers)"))
    }

    @Test
    fun `formats multiple changed classes`() {
        val impacts = listOf(
            ChangedClassImpact(
                className = ClassName("com.example.Service"),
                sourceFile = "Service.kt",
                callers = setOf(
                    MethodRef(ClassName("com.example.Controller"), "handle"),
                    MethodRef(ClassName("com.example.Worker"), "process"),
                ),
            ),
            ChangedClassImpact(
                className = ClassName("com.example.Repo"),
                sourceFile = "Repo.kt",
                callers = setOf(
                    MethodRef(ClassName("com.example.Service"), "save"),
                ),
            ),
        )

        val result = ChangedSinceFormatter.format(impacts, unresolved = emptyList())

        assertTrue(result.contains("com.example.Service"))
        assertTrue(result.contains("com.example.Repo"))
        assertTrue(result.contains("com.example.Controller.handle"))
        assertTrue(result.contains("com.example.Service.save"))
    }

    @Test
    fun `formats unresolved files section`() {
        val impacts = emptyList<ChangedClassImpact>()

        val result = ChangedSinceFormatter.format(
            impacts,
            unresolved = listOf("build.gradle.kts", "README.md"),
        )

        assertTrue(result.contains("build.gradle.kts"))
        assertTrue(result.contains("README.md"))
    }

    @Test
    fun `shows caller count in header`() {
        val impacts = listOf(
            ChangedClassImpact(
                className = ClassName("com.example.Service"),
                sourceFile = "Service.kt",
                callers = setOf(
                    MethodRef(ClassName("com.example.Controller"), "handle"),
                    MethodRef(ClassName("com.example.Worker"), "process"),
                ),
            ),
        )

        val result = ChangedSinceFormatter.format(impacts, unresolved = emptyList())

        assertTrue(result.contains("2 caller"), "Should show caller count")
    }
}
