package no.f12.codenavigator.navigation.refactor

import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TargetPatternTest {

    @TempDir
    lateinit var dir: File

    @Test
    fun `pattern Class_method works on rename-method, like on find-callers`() {
        val config = RenameMethodConfig.parse(mapOf("pattern" to "a.Person.actionsOn", "new-name" to "actions"))

        assertEquals("a.Person", config.className)
        assertEquals("actionsOn", config.methodName)
    }

    @Test
    fun `target-class and method still work`() {
        val config = RenameParamConfig.parse(mapOf("target-class" to "a.Person", "method" to "m", "param" to "p", "new-name" to "q"))

        assertEquals("a.Person" to "m", config.className to config.methodName)
    }

    @Test
    fun `safe-delete takes a pattern without a method as the class`() {
        val config = SafeDeleteConfig.parse(mapOf("pattern" to "a.Person"))

        assertEquals("a.Person", config.className)
        assertEquals(null, config.methodName)
    }

    @Test
    fun `a simple class name is resolved to its fully qualified name from the sources`() {
        File(dir, "a/b").mkdirs()
        File(dir, "a/b/Person.kt").writeText("package a.b\n\nclass Person {\n    fun actionsOn() = 1\n}\n")

        assertEquals("a.b.Person", TargetClassResolver.resolve(listOf(dir), "Person"))
        assertEquals("x.Y", TargetClassResolver.resolve(listOf(dir), "x.Y"))
    }

    @Test
    fun `an ambiguous simple name is an error listing the candidates`() {
        File(dir, "a").mkdirs(); File(dir, "b").mkdirs()
        File(dir, "a/Person.kt").writeText("package a\n\nclass Person\n")
        File(dir, "b/Person.kt").writeText("package b\n\nclass Person\n")

        val e = assertFailsWith<IllegalArgumentException> { TargetClassResolver.resolve(listOf(dir), "Person") }
        assertTrue(e.message!!.contains("a.Person") && e.message!!.contains("b.Person"), e.message)
    }
}
