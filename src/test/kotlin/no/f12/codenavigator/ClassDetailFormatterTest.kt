package no.f12.codenavigator

import kotlin.test.Test
import kotlin.test.assertEquals

class ClassDetailFormatterTest {

    @Test
    fun `formats a class with fields and methods`() {
        val detail = ClassDetail(
            className = "com.example.MyService",
            sourceFile = "MyService.kt",
            superClass = null,
            interfaces = emptyList(),
            fields = listOf(
                FieldDetail("name", "String"),
                FieldDetail("count", "int"),
            ),
            methods = listOf(
                MethodDetail("findUser", listOf("String", "int"), "User"),
                MethodDetail("doWork", emptyList(), "void"),
            ),
        )

        val output = ClassDetailFormatter.format(listOf(detail))

        val expected = """
            |=== com.example.MyService (MyService.kt) ===
            |
            |Fields:
            |  name: String
            |  count: int
            |
            |Methods:
            |  findUser(String, int): User
            |  doWork(): void
        """.trimMargin()
        assertEquals(expected, output)
    }

    // [TEST-DONE] Formats a class with fields and methods
    @Test
    fun `formats a class with superclass and interfaces`() {
        val detail = ClassDetail(
            className = "com.example.AdminService",
            sourceFile = "AdminService.kt",
            superClass = "com.example.BaseService",
            interfaces = listOf("com.example.Auditable", "java.io.Serializable"),
            fields = emptyList(),
            methods = listOf(MethodDetail("audit", emptyList(), "void")),
        )

        val output = ClassDetailFormatter.format(listOf(detail))

        val expected = """
            |=== com.example.AdminService (AdminService.kt) ===
            |Extends: com.example.BaseService
            |Implements: com.example.Auditable, java.io.Serializable
            |
            |Methods:
            |  audit(): void
        """.trimMargin()
        assertEquals(expected, output)
    }

    // [TEST-DONE] Formats a class with superclass and interfaces
    @Test
    fun `omits Fields section when no fields`() {
        val detail = ClassDetail(
            className = "com.example.Svc",
            sourceFile = "Svc.kt",
            superClass = null,
            interfaces = emptyList(),
            fields = emptyList(),
            methods = listOf(MethodDetail("run", emptyList(), "void")),
        )

        val output = ClassDetailFormatter.format(listOf(detail))

        val expected = """
            |=== com.example.Svc (Svc.kt) ===
            |
            |Methods:
            |  run(): void
        """.trimMargin()
        assertEquals(expected, output)
    }

    // [TEST-DONE] Omits Fields section when no fields

    @Test
    fun `omits Methods section when no methods`() {
        val detail = ClassDetail(
            className = "com.example.Config",
            sourceFile = "Config.kt",
            superClass = null,
            interfaces = emptyList(),
            fields = listOf(FieldDetail("name", "String")),
            methods = emptyList(),
        )

        val output = ClassDetailFormatter.format(listOf(detail))

        val expected = """
            |=== com.example.Config (Config.kt) ===
            |
            |Fields:
            |  name: String
        """.trimMargin()
        assertEquals(expected, output)
    }

    // [TEST-DONE] Omits Methods section when no methods

    @Test
    fun `formats multiple matched classes separated by blank line`() {
        val details = listOf(
            ClassDetail("com.example.A", "A.kt", null, emptyList(), emptyList(),
                listOf(MethodDetail("doA", emptyList(), "void"))),
            ClassDetail("com.example.B", "B.kt", null, emptyList(), emptyList(),
                listOf(MethodDetail("doB", emptyList(), "void"))),
        )

        val output = ClassDetailFormatter.format(details)

        val expected = """
            |=== com.example.A (A.kt) ===
            |
            |Methods:
            |  doA(): void
            |
            |=== com.example.B (B.kt) ===
            |
            |Methods:
            |  doB(): void
        """.trimMargin()
        assertEquals(expected, output)
    }

    // [TEST-DONE] Formats multiple matched classes separated by blank line

    @Test
    fun `omits Extends and Implements lines when not applicable`() {
        val detail = ClassDetail(
            className = "com.example.Simple",
            sourceFile = "Simple.kt",
            superClass = null,
            interfaces = emptyList(),
            fields = emptyList(),
            methods = emptyList(),
        )

        val output = ClassDetailFormatter.format(listOf(detail))

        assertEquals("=== com.example.Simple (Simple.kt) ===", output)
    }

    // [TEST-DONE] Omits Extends line when superclass is null
    // [TEST-DONE] Omits Implements line when no interfaces
}
