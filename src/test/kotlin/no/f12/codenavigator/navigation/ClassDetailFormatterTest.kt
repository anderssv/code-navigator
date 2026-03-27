package no.f12.codenavigator.navigation

import kotlin.test.Test
import kotlin.test.assertEquals

class ClassDetailFormatterTest {

    @Test
    fun `formats a class with fields and methods`() {
        val detail = ClassDetail(
            className = ClassName("com.example.MyService"),
            sourceFile = "MyService.kt",
            superClass = null,
            interfaces = emptyList(),
            fields = listOf(
                FieldDetail("name", "String", emptyList()),
                FieldDetail("count", "int", emptyList()),
            ),
            methods = listOf(
                MethodDetail("findUser", listOf("String", "int"), "User", emptyList()),
                MethodDetail("doWork", emptyList(), "void", emptyList()),
            ),
            annotations = emptyList(),
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
            className = ClassName("com.example.AdminService"),
            sourceFile = "AdminService.kt",
            superClass = ClassName("com.example.BaseService"),
            interfaces = listOf(ClassName("com.example.Auditable"), ClassName("java.io.Serializable")),
            fields = emptyList(),
            methods = listOf(MethodDetail("audit", emptyList(), "void", emptyList())),
            annotations = emptyList(),
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
            className = ClassName("com.example.Svc"),
            sourceFile = "Svc.kt",
            superClass = null,
            interfaces = emptyList(),
            fields = emptyList(),
            methods = listOf(MethodDetail("run", emptyList(), "void", emptyList())),
            annotations = emptyList(),
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
            className = ClassName("com.example.Config"),
            sourceFile = "Config.kt",
            superClass = null,
            interfaces = emptyList(),
            fields = listOf(FieldDetail("name", "String", emptyList())),
            methods = emptyList(),
            annotations = emptyList(),
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
            ClassDetail(ClassName("com.example.A"), "A.kt", null, emptyList(), emptyList(),
                listOf(MethodDetail("doA", emptyList(), "void", emptyList())), emptyList()),
            ClassDetail(ClassName("com.example.B"), "B.kt", null, emptyList(), emptyList(),
                listOf(MethodDetail("doB", emptyList(), "void", emptyList())), emptyList()),
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
            className = ClassName("com.example.Simple"),
            sourceFile = "Simple.kt",
            superClass = null,
            interfaces = emptyList(),
            fields = emptyList(),
            methods = emptyList(),
            annotations = emptyList(),
        )

        val output = ClassDetailFormatter.format(listOf(detail))

        assertEquals("=== com.example.Simple (Simple.kt) ===", output)
    }

    // [TEST-DONE] Omits Extends line when superclass is null
    // [TEST-DONE] Omits Implements line when no interfaces

    @Test
    fun `formats class-level annotations before extends`() {
        val detail = ClassDetail(
            className = ClassName("com.example.MyService"),
            sourceFile = "MyService.kt",
            superClass = ClassName("com.example.BaseService"),
            interfaces = emptyList(),
            fields = emptyList(),
            methods = listOf(MethodDetail("doWork", emptyList(), "void", emptyList())),
            annotations = listOf(
                AnnotationDetail("Service", emptyMap()),
                AnnotationDetail("Transactional", emptyMap()),
            ),
        )

        val output = ClassDetailFormatter.format(listOf(detail))

        val expected = """
            |=== com.example.MyService (MyService.kt) ===
            |@Service
            |@Transactional
            |Extends: com.example.BaseService
            |
            |Methods:
            |  doWork(): void
        """.trimMargin()
        assertEquals(expected, output)
    }

    @Test
    fun `formats method annotations before method signature`() {
        val detail = ClassDetail(
            className = ClassName("com.example.Svc"),
            sourceFile = "Svc.kt",
            superClass = null,
            interfaces = emptyList(),
            fields = emptyList(),
            methods = listOf(
                MethodDetail("resilientCall", emptyList(), "void", listOf(
                    AnnotationDetail("CircuitBreaker", mapOf("name" to "backend")),
                )),
                MethodDetail("doWork", emptyList(), "void", emptyList()),
            ),
            annotations = emptyList(),
        )

        val output = ClassDetailFormatter.format(listOf(detail))

        val expected = """
            |=== com.example.Svc (Svc.kt) ===
            |
            |Methods:
            |  @CircuitBreaker(name="backend")
            |  resilientCall(): void
            |  doWork(): void
        """.trimMargin()
        assertEquals(expected, output)
    }

    @Test
    fun `formats field annotations before field`() {
        val detail = ClassDetail(
            className = ClassName("com.example.Svc"),
            sourceFile = "Svc.kt",
            superClass = null,
            interfaces = emptyList(),
            fields = listOf(
                FieldDetail("repo", "Repository", listOf(AnnotationDetail("Inject", emptyMap()))),
                FieldDetail("name", "String", emptyList()),
            ),
            methods = emptyList(),
            annotations = emptyList(),
        )

        val output = ClassDetailFormatter.format(listOf(detail))

        val expected = """
            |=== com.example.Svc (Svc.kt) ===
            |
            |Fields:
            |  @Inject
            |  repo: Repository
            |  name: String
        """.trimMargin()
        assertEquals(expected, output)
    }
}
