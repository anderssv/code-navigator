package no.f12.codenavigator.navigation.declaration

import no.f12.codenavigator.config.OutputFormat
import no.f12.codenavigator.navigation.types.Scope
import no.f12.codenavigator.registry.TaskRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FindDeclarationConfigTest {

    @Test
    fun `parses all properties`() {
        val config = FindDeclarationConfig.parse(mapOf("pattern" to "place", "kind" to "function", "scope" to "prod", "format" to "json"))
        assertEquals("place", config.pattern)
        assertEquals(DeclarationKindGroup.FUNCTION, config.kind)
        assertEquals(Scope.PROD, config.scope)
        assertEquals(OutputFormat.JSON, config.format)
    }

    @Test
    fun `defaults to all kinds, all scopes, text`() {
        val config = FindDeclarationConfig.parse(mapOf("pattern" to "place"))
        assertNull(config.kind)
        assertEquals(Scope.ALL, config.scope)
        assertEquals(OutputFormat.TEXT, config.format)
    }

    @Test
    fun `kind=all is the same as absent`() {
        assertNull(FindDeclarationConfig.parse(mapOf("pattern" to "x", "kind" to "all")).kind)
        assertEquals(DeclarationKindGroup.CLASS, FindDeclarationConfig.parse(mapOf("pattern" to "x", "kind" to "Class")).kind)
    }

    @Test
    fun `throws on missing pattern and unknown kind`() {
        assertTrue(assertFailsWith<IllegalArgumentException> { FindDeclarationConfig.parse(emptyMap()) }.message!!.contains("pattern"))
        assertTrue(assertFailsWith<IllegalArgumentException> { FindDeclarationConfig.parse(mapOf("pattern" to "x", "kind" to "method")) }.message!!.contains("method"))
    }

    @Test
    fun `camelCase pattern is enhanced through TaskRegistry like the other find tasks`() {
        val props = TaskRegistry.FIND_DECLARATION.enhanceProperties(mapOf("pattern" to "placeOrder"))
        assertEquals("place.*Order", props["pattern"])
    }
}
