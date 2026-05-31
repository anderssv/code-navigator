package no.f12.codenavigator.navigation.refactor

import kotlin.test.Test
import kotlin.test.assertTrue
import java.io.File

class RenameLocationFinderTest {

    private val testProjectClasses = File("test-project/build/classes/kotlin/main")

    @Test
    fun `finds call site files for method invocation`() {
        val files = RenameLocationFinder.findCallSiteFiles(
            classesRoots = listOf(testProjectClasses),
            className = "com.example.services.AuditService",
            methodName = "formatAuditEntry",
        )

        // formatAuditEntry is called within AuditService itself (self-call)
        assertTrue(
            files.any { it.endsWith("AuditService.kt") && it.contains("services") },
            "Should find AuditService.kt as a call site. Found: $files",
        )
    }

    @Test
    fun `finds call site files for cross-file method invocation`() {
        val files = RenameLocationFinder.findCallSiteFiles(
            classesRoots = listOf(testProjectClasses),
            className = "com.example.variants.crossfilecallmethod.AuditService",
            methodName = "formatAuditEntry",
        )

        assertTrue(
            files.any { it.endsWith("ReportService.kt") && it.contains("crossfilecallmethod") },
            "Should find ReportService.kt as a cross-file call site. Found: $files",
        )
    }

    @Test
    fun `finds call site files for interface method`() {
        val files = RenameLocationFinder.findCallSiteFiles(
            classesRoots = listOf(testProjectClasses),
            className = "com.example.domain.UserRepository",
            methodName = "findById",
        )

        assertTrue(
            files.any { it.endsWith("AuditService.kt") && it.contains("services") },
            "Should find AuditService.kt calling findById. Found: $files",
        )
    }

    @Test
    fun `finds implementors of interface`() {
        val impls = RenameLocationFinder.findImplementors(
            classesRoots = listOf(testProjectClasses),
            className = "com.example.domain.UserRepository",
        )

        assertTrue(
            impls.contains("com.example.infra.InMemoryUserRepository"),
            "Should find InMemoryUserRepository as implementor. Found: $impls",
        )
    }

    @Test
    fun `finds companion object call sites`() {
        val files = RenameLocationFinder.findCallSiteFiles(
            classesRoots = listOf(testProjectClasses),
            className = "com.example.variants.companion.UserFactory",
            methodName = "create",
        )

        assertTrue(
            files.any { it.endsWith("UserService.kt") && it.contains("companion") },
            "Should find UserService.kt calling companion method. Found: $files",
        )
    }
}
