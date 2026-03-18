package no.f12.codenavigator

import kotlin.test.Test
import kotlin.test.assertEquals

class JsonFormatterTest {

    // === ClassInfo formatting ===

    @Test
    fun `empty class list produces empty JSON array`() {
        val result = JsonFormatter.formatClasses(emptyList())

        assertEquals("[]", result)
    }
    @Test
    fun `single class produces JSON array with one object`() {
        val classes = listOf(
            ClassInfo("com.example.Foo", "Foo.kt", "com/example/Foo.kt", isUserDefinedClass = true),
        )

        val result = JsonFormatter.formatClasses(classes)

        assertEquals(
            """[{"className":"com.example.Foo","sourceFile":"Foo.kt","sourcePath":"com/example/Foo.kt"}]""",
            result,
        )
    }
    @Test
    fun `multiple classes produce JSON array sorted by className`() {
        val classes = listOf(
            ClassInfo("com.example.Zebra", "Zebra.kt", "com/example/Zebra.kt", isUserDefinedClass = true),
            ClassInfo("com.example.Alpha", "Alpha.kt", "com/example/Alpha.kt", isUserDefinedClass = true),
        )

        val result = JsonFormatter.formatClasses(classes)

        assertEquals(
            """[{"className":"com.example.Alpha","sourceFile":"Alpha.kt","sourcePath":"com/example/Alpha.kt"},""" +
                """{"className":"com.example.Zebra","sourceFile":"Zebra.kt","sourcePath":"com/example/Zebra.kt"}]""",
            result,
        )
    }

    @Test
    fun `special characters in class names are escaped in JSON`() {
        val classes = listOf(
            ClassInfo("com.example.Foo\"Bar", "Foo\"Bar.kt", "com/example/Foo\"Bar.kt", isUserDefinedClass = true),
        )

        val result = JsonFormatter.formatClasses(classes)

        assertEquals(
            """[{"className":"com.example.Foo\"Bar","sourceFile":"Foo\"Bar.kt","sourcePath":"com/example/Foo\"Bar.kt"}]""",
            result,
        )
    }

    // === SymbolInfo formatting ===

    @Test
    fun `empty symbol list produces empty JSON array`() {
        val result = JsonFormatter.formatSymbols(emptyList())

        assertEquals("[]", result)
    }

    @Test
    fun `single symbol produces JSON object with all fields`() {
        val symbols = listOf(
            SymbolInfo("com.example", "Service", "doWork", SymbolKind.METHOD, "Service.kt"),
        )

        val result = JsonFormatter.formatSymbols(symbols)

        assertEquals(
            """[{"package":"com.example","class":"Service","symbol":"doWork","kind":"method","sourceFile":"Service.kt"}]""",
            result,
        )
    }

    @Test
    fun `field symbol kind is lowercase string`() {
        val symbols = listOf(
            SymbolInfo("com.example", "Entity", "name", SymbolKind.FIELD, "Entity.kt"),
        )

        val result = JsonFormatter.formatSymbols(symbols)

        assertEquals(
            """[{"package":"com.example","class":"Entity","symbol":"name","kind":"field","sourceFile":"Entity.kt"}]""",
            result,
        )
    }

    // === ClassDetail formatting ===

    @Test
    fun `empty class detail list produces empty JSON array`() {
        val result = JsonFormatter.formatClassDetails(emptyList())

        assertEquals("[]", result)
    }

    @Test
    fun `single class detail with fields and methods`() {
        val details = listOf(
            ClassDetail(
                className = "com.example.Order",
                sourceFile = "Order.kt",
                superClass = "com.example.BaseEntity",
                interfaces = listOf("com.example.Identifiable"),
                fields = listOf(FieldDetail("id", "Long"), FieldDetail("name", "String")),
                methods = listOf(MethodDetail("getName", emptyList(), "String")),
            ),
        )

        val result = JsonFormatter.formatClassDetails(details)

        assertEquals(
            """[{"className":"com.example.Order","sourceFile":"Order.kt","superClass":"com.example.BaseEntity",""" +
                """"interfaces":["com.example.Identifiable"],""" +
                """"fields":[{"name":"id","type":"Long"},{"name":"name","type":"String"}],""" +
                """"methods":[{"name":"getName","parameters":[],"returnType":"String"}]}]""",
            result,
        )
    }

    @Test
    fun `class detail with null superClass omits superClass field`() {
        val details = listOf(
            ClassDetail(
                className = "com.example.Simple",
                sourceFile = "Simple.kt",
                superClass = null,
                interfaces = emptyList(),
                fields = emptyList(),
                methods = emptyList(),
            ),
        )

        val result = JsonFormatter.formatClassDetails(details)

        assertEquals(
            """[{"className":"com.example.Simple","sourceFile":"Simple.kt","interfaces":[],"fields":[],"methods":[]}]""",
            result,
        )
    }

    // === Call tree formatting ===

    @Test
    fun `method with no callees produces empty children array`() {
        val graph = CallGraph(emptyMap())
        val method = MethodRef("com.example.Service", "doWork")

        val result = JsonFormatter.formatCallTree(graph, listOf(method), maxDepth = 3, CallDirection.CALLEES)

        assertEquals(
            """[{"method":"com.example.Service.doWork","children":[]}]""",
            result,
        )
    }

    @Test
    fun `method with callees produces nested tree structure`() {
        val caller = MethodRef("com.example.Controller", "handle")
        val callee = MethodRef("com.example.Service", "doWork")
        val graph = CallGraph(
            mapOf(caller to setOf(callee)),
            sourceFiles = mapOf("com.example.Service" to "Service.kt"),
        )

        val result = JsonFormatter.formatCallTree(graph, listOf(caller), maxDepth = 3, CallDirection.CALLEES)

        assertEquals(
            """[{"method":"com.example.Controller.handle","children":[""" +
                """{"method":"com.example.Service.doWork","sourceFile":"Service.kt","children":[]}]}]""",
            result,
        )
    }

    @Test
    fun `transitive call tree produces nested children`() {
        val a = MethodRef("com.example.A", "start")
        val b = MethodRef("com.example.B", "middle")
        val c = MethodRef("com.example.C", "end")
        val graph = CallGraph(
            mapOf(a to setOf(b), b to setOf(c)),
            sourceFiles = mapOf("com.example.B" to "B.kt", "com.example.C" to "C.kt"),
        )

        val result = JsonFormatter.formatCallTree(graph, listOf(a), maxDepth = 3, CallDirection.CALLEES)

        assertEquals(
            """[{"method":"com.example.A.start","children":[""" +
                """{"method":"com.example.B.middle","sourceFile":"B.kt","children":[""" +
                """{"method":"com.example.C.end","sourceFile":"C.kt","children":[]}]}]}]""",
            result,
        )
    }

    @Test
    fun `depth limit stops recursion in JSON output`() {
        val a = MethodRef("com.example.A", "start")
        val b = MethodRef("com.example.B", "middle")
        val c = MethodRef("com.example.C", "end")
        val graph = CallGraph(
            mapOf(a to setOf(b), b to setOf(c)),
            sourceFiles = mapOf("com.example.B" to "B.kt", "com.example.C" to "C.kt"),
        )

        val result = JsonFormatter.formatCallTree(graph, listOf(a), maxDepth = 1, CallDirection.CALLEES)

        assertEquals(
            """[{"method":"com.example.A.start","children":[""" +
                """{"method":"com.example.B.middle","sourceFile":"B.kt","children":[]}]}]""",
            result,
        )
    }

    @Test
    fun `cycle detection prevents infinite recursion in JSON`() {
        val a = MethodRef("com.example.A", "callB")
        val b = MethodRef("com.example.B", "callA")
        val graph = CallGraph(
            mapOf(a to setOf(b), b to setOf(a)),
            sourceFiles = mapOf("com.example.A" to "A.kt", "com.example.B" to "B.kt"),
        )

        val result = JsonFormatter.formatCallTree(graph, listOf(a), maxDepth = 10, CallDirection.CALLEES)

        assertEquals(
            """[{"method":"com.example.A.callB","children":[""" +
                """{"method":"com.example.B.callA","sourceFile":"B.kt","children":[""" +
                """{"method":"com.example.A.callB","sourceFile":"A.kt","children":[]}]}]}]""",
            result,
        )
    }

    // === Interface formatting ===

    @Test
    fun `empty interface list produces empty JSON array`() {
        val registry = InterfaceRegistry(emptyMap())

        val result = JsonFormatter.formatInterfaces(registry, emptyList())

        assertEquals("[]", result)
    }

    @Test
    fun `interface with implementors produces nested structure`() {
        val registry = InterfaceRegistry(
            mapOf(
                "com.example.Repository" to listOf(
                    ImplementorInfo("com.example.SqlRepo", "SqlRepo.kt"),
                    ImplementorInfo("com.example.FakeRepo", "FakeRepo.kt"),
                ),
            ),
        )

        val result = JsonFormatter.formatInterfaces(registry, listOf("com.example.Repository"))

        assertEquals(
            """[{"interface":"com.example.Repository","implementors":[""" +
                """{"className":"com.example.FakeRepo","sourceFile":"FakeRepo.kt"},""" +
                """{"className":"com.example.SqlRepo","sourceFile":"SqlRepo.kt"}]}]""",
            result,
        )
    }

    // === Package dependencies formatting ===

    @Test
    fun `empty package list produces empty JSON array`() {
        val deps = PackageDependencies(emptyMap())

        val result = JsonFormatter.formatPackageDeps(deps, emptyList())

        assertEquals("[]", result)
    }

    @Test
    fun `package with dependencies produces nested structure`() {
        val deps = PackageDependencies(
            mapOf("com.example.services" to listOf("com.example.domain", "com.example.repo")),
        )

        val result = JsonFormatter.formatPackageDeps(deps, listOf("com.example.services"))

        assertEquals(
            """[{"package":"com.example.services","dependencies":["com.example.domain","com.example.repo"]}]""",
            result,
        )
    }

    @Test
    fun `reverse mode shows dependents instead of dependencies`() {
        val deps = PackageDependencies(
            mapOf(
                "com.example.services" to listOf("com.example.domain"),
                "com.example.ktor" to listOf("com.example.domain"),
            ),
        )

        val result = JsonFormatter.formatPackageDeps(
            deps,
            listOf("com.example.domain"),
            reverse = true,
        )

        assertEquals(
            """[{"package":"com.example.domain","dependents":["com.example.ktor","com.example.services"]}]""",
            result,
        )
    }
}
