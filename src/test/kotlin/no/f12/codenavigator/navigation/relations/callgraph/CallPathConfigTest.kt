package no.f12.codenavigator.navigation.relations.callgraph

import no.f12.codenavigator.config.OutputFormat
import no.f12.codenavigator.navigation.types.Scope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CallPathConfigTest {

    @Test
    fun `parses every property`() {
        val config = CallPathConfig.parse(mapOf(
            "from" to "Controller",
            "from-annotation" to "PostMapping",
            "to" to "Repo.delete",
            "guard" to "authorize",
            "guard-depth" to "2",
            "maxdepth" to "5",
            "top" to "10",
            "project-only" to "false",
            "scope" to "prod",
            "format" to "json",
        ))

        assertEquals("Controller", config.from)
        assertEquals("PostMapping", config.fromAnnotation)
        assertEquals("Repo.delete", config.to)
        assertEquals("authorize", config.guard)
        assertEquals(2, config.guardDepth)
        assertEquals(5, config.maxDepth)
        assertEquals(10, config.top)
        assertEquals(false, config.projectOnly)
        assertEquals(Scope.PROD, config.scope)
        assertEquals(OutputFormat.JSON, config.format)
    }

    @Test
    fun `defaults`() {
        val config = CallPathConfig.parse(mapOf("from" to "Controller", "to" to "Repo.delete"))

        assertNull(config.fromAnnotation)
        assertNull(config.guard)
        assertEquals(1, config.guardDepth)
        assertEquals(8, config.maxDepth)
        assertEquals(50, config.top)
        assertEquals(true, config.projectOnly)
        assertEquals(Scope.ALL, config.scope)
        assertEquals(OutputFormat.TEXT, config.format)
    }

    @Test
    fun `from-annotation alone is enough to select entries`() {
        val config = CallPathConfig.parse(mapOf("from-annotation" to "PostMapping", "to" to "Repo.delete"))

        assertNull(config.from)
        assertEquals("PostMapping", config.fromAnnotation)
    }

    @Test
    fun `missing to is rejected`() {
        val error = assertFailsWith<IllegalArgumentException> { CallPathConfig.parse(mapOf("from" to "Controller")) }

        assertTrue("'to'" in error.message!!)
    }

    @Test
    fun `missing both from and from-annotation is rejected`() {
        val error = assertFailsWith<IllegalArgumentException> { CallPathConfig.parse(mapOf("to" to "Repo.delete")) }

        assertTrue("'from'" in error.message!! && "'from-annotation'" in error.message!!)
    }

    @Test
    fun `invalid regexes are rejected with the parameter name`() {
        for (key in listOf("from-annotation", "guard")) {
            val error = assertFailsWith<IllegalArgumentException> {
                CallPathConfig.parse(mapOf("from" to "Controller", "to" to "Repo.delete", key to "(unclosed"))
            }
            assertTrue("'$key'" in error.message!!, "message should name $key: ${error.message}")
        }
    }

    @Test
    fun `negative guard-depth and non-positive maxdepth are rejected`() {
        assertFailsWith<IllegalArgumentException> {
            CallPathConfig.parse(mapOf("from" to "C", "to" to "R", "guard" to "a", "guard-depth" to "-1"))
        }
        assertFailsWith<IllegalArgumentException> {
            CallPathConfig.parse(mapOf("from" to "C", "to" to "R", "maxdepth" to "0"))
        }
    }

    @Test
    fun `branches is off by default and can be turned on`() {
        assertEquals(false, CallPathConfig.parse(mapOf("from" to "C", "to" to "R")).branches)
        assertEquals(true, CallPathConfig.parse(mapOf("from" to "C", "to" to "R", "branches" to "true")).branches)
    }
}
