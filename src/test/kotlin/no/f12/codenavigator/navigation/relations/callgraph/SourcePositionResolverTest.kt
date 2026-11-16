package no.f12.codenavigator.navigation.relations.callgraph

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SourcePositionResolverTest {

    private val classes = listOf(File("test-project/build/classes/kotlin/main"))
    private val file = "src/main/kotlin/com/example/variants/position/Person.kt"
    private val person = "com.example.variants.position.Person"

    private fun at(line: Int) = SourcePositionResolver.resolve(classes, file, line)

    @Test
    fun `a line inside a method body resolves to that method`() {
        assertEquals(SourcePositionResolver.Target.Method(person, "actionsOn"), at(6))
    }

    @Test
    fun `the declaration line of a block-bodied function resolves to it`() {
        assertEquals(SourcePositionResolver.Target.Method(person, "actionsOn"), at(5))
    }

    @Test
    fun `an expression-bodied function resolves on its own line`() {
        assertEquals(SourcePositionResolver.Target.Method(person, "greet"), at(10))
    }

    @Test
    fun `the class line resolves to the class`() {
        assertEquals(SourcePositionResolver.Target.Type(person), at(3))
    }

    @Test
    fun `a file that has no compiled classes is an error naming it`() {
        val e = assertFailsWith<IllegalArgumentException> { SourcePositionResolver.resolve(classes, "src/main/kotlin/x/Nope.kt", 3) }
        assertTrue(e.message!!.contains("Nope.kt"), e.message)
    }

    @Test
    fun `file and line become the find-usages properties`() {
        val props = SourcePositionResolver.toUsageProperties(classes, mapOf("file" to file, "line" to "6", "format" to "llm"))

        assertEquals(mapOf("owner-class" to person, "method" to "actionsOn", "format" to "llm"), props)
    }
}
