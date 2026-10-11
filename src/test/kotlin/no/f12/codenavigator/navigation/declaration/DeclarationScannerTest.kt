package no.f12.codenavigator.navigation.declaration

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Runs the PSI scanner over the real fixture at
 * `test-project/src/main/kotlin/com/example/variants/declarations/Declarations.kt`.
 * Line numbers asserted here are the fixture's actual lines — open the file to check them.
 */
class DeclarationScannerTest {

    private val sourceRoot = File("test-project/src/main/kotlin")
    private val fixture = File(sourceRoot, "com/example/variants/declarations/Declarations.kt")
    private val pkg = "com.example.variants.declarations"

    private val all: List<DeclarationInfo> by lazy {
        DeclarationScanner.scan(listOf(sourceRoot)).filter { it.sourcePath == "com/example/variants/declarations/Declarations.kt" }
    }

    private fun one(fqn: String, kind: DeclarationKind? = null): DeclarationInfo {
        val hits = all.filter { it.fqn == fqn && (kind == null || it.kind == kind) }
        assertEquals(1, hits.size, "expected exactly one $fqn, got ${hits.map { it.signature }}")
        return hits.single()
    }

    @Test
    fun `class range starts at its KDoc and ends at the closing brace, name line is the header`() {
        val cls = one("$pkg.OrderService", DeclarationKind.CLASS)
        assertEquals(3, cls.startLine, "KDoc is part of the declaration")
        assertEquals(1, cls.startColumn)
        assertEquals(5, cls.nameLine, "the annotation on line 4 is included, the name sits on line 5")
        assertEquals(23, cls.endLine)
        assertEquals(1, cls.endColumn, "closing brace is in column 1")
        assertEquals("OrderService(val repository: String)", cls.signature)
    }

    @Test
    fun `offsets slice exactly the declaration text`() {
        val content = fixture.readText().replace("\r\n", "\n")
        val helper = one("$pkg.topLevelHelper")
        assertEquals("fun topLevelHelper(): Int = DEFAULT_LIMIT", content.substring(helper.startOffset, helper.endOffset))
        assertEquals(28, helper.startLine)
        assertEquals(28, helper.endLine)

        val cls = one("$pkg.OrderService", DeclarationKind.CLASS)
        val text = content.substring(cls.startOffset, cls.endOffset)
        assertTrue(text.startsWith("/**"), "starts at KDoc: ${text.take(20)}")
        assertTrue(text.endsWith("}"), "ends at closing brace")
    }

    @Test
    fun `overloads share an fqn but carry distinct signatures and ranges`() {
        val overloads = all.filter { it.fqn == "$pkg.OrderService.place" }.sortedBy { it.startLine }
        assertEquals(2, overloads.size)

        val expressionBody = overloads[0]
        assertEquals("place(orderId: String): Int", expressionBody.signature)
        assertEquals(7, expressionBody.startLine, "member KDoc is included")
        assertEquals(8, expressionBody.nameLine)
        assertEquals(8, expressionBody.endLine)

        val blockBody = overloads[1]
        assertEquals("place(orderId: String, quantity: Int): Int", blockBody.signature)
        assertEquals(10, blockBody.startLine)
        assertEquals(13, blockBody.endLine)
    }

    @Test
    fun `nested classes, companions and their members get dotted fqns`() {
        assertEquals(15..17, one("$pkg.OrderService.Receipt", DeclarationKind.CLASS).let { it.startLine..it.endLine })
        assertEquals(16, one("$pkg.OrderService.Receipt.formatted").startLine)
        val companion = one("$pkg.OrderService.Companion", DeclarationKind.COMPANION_OBJECT)
        assertEquals(19..22, companion.startLine..companion.endLine)
        assertEquals(21, one("$pkg.OrderService.Companion.create").startLine)
        assertEquals(DeclarationKind.PROPERTY, one("$pkg.OrderService.Companion.DEFAULT_LIMIT").kind)
    }

    @Test
    fun `primary constructor properties are reported as properties`() {
        val prop = one("$pkg.OrderService.repository")
        assertEquals(DeclarationKind.PROPERTY, prop.kind)
        assertEquals("val repository: String", prop.signature)
        assertEquals(5, prop.startLine)
    }

    @Test
    fun `top-level declarations use the package as owner`() {
        assertEquals(DeclarationKind.ENUM, one("$pkg.OrderStatus").kind)
        assertEquals(DeclarationKind.ENUM_ENTRY, one("$pkg.OrderStatus.NEW").kind)
        assertEquals(DeclarationKind.TYPEALIAS, one("$pkg.OrderId").kind)
        assertEquals("typealias OrderId = String", one("$pkg.OrderId").signature)
        assertEquals("val defaultStatus: OrderStatus", one("$pkg.defaultStatus").signature)
        assertEquals("String.toOrderId(): OrderId", one("$pkg.toOrderId").signature)
        assertEquals(29, one("$pkg.DEFAULT_LIMIT").startLine, "private top-level const is distinct from the companion one")
    }

    @Test
    fun `local declarations inside function bodies are not reported`() {
        assertNull(all.firstOrNull { it.name == "base" }, "`val base` inside place() is local")
    }

    @Test
    fun `results are ordered by file then position`() {
        val lines = all.map { it.startOffset }
        assertEquals(lines.sorted(), lines)
        assertTrue(all.all { it.file == fixture.absolutePath })
    }

    @Test
    fun `empty source roots yield no declarations`() {
        assertEquals(emptyList(), DeclarationScanner.scan(listOf(File("does/not/exist"))))
    }
}
