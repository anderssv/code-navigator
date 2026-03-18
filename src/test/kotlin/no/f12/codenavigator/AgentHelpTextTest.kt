package no.f12.codenavigator

import kotlin.test.Test
import kotlin.test.assertTrue

class AgentHelpTextTest {

    @Test
    fun `agent help text contains all task names`() {
        val text = AgentHelpText.generate()

        assertTrue(text.contains("cnavListClasses"))
        assertTrue(text.contains("cnavFindClass"))
        assertTrue(text.contains("cnavFindSymbol"))
        assertTrue(text.contains("cnavCallers"))
        assertTrue(text.contains("cnavCallees"))
        assertTrue(text.contains("cnavClass"))
        assertTrue(text.contains("cnavInterfaces"))
        assertTrue(text.contains("cnavDeps"))
    }

    @Test
    fun `agent help text recommends JSON format for agents`() {
        val text = AgentHelpText.generate()

        assertTrue(text.contains("-Pformat=json"))
        assertTrue(text.contains("Machine-readable JSON"))
    }

    @Test
    fun `agent help text includes workflow guidance`() {
        val text = AgentHelpText.generate()

        assertTrue(text.contains("ORIENT"))
        assertTrue(text.contains("FIND"))
        assertTrue(text.contains("INSPECT"))
        assertTrue(text.contains("TRACE"))
        assertTrue(text.contains("MAP"))
    }

    @Test
    fun `agent help text includes performance tips`() {
        val text = AgentHelpText.generate()

        assertTrue(text.contains("Tips for Optimal Results"))
        assertTrue(text.contains("-Pprojectonly=true"))
        assertTrue(text.contains("cached"))
    }

    @Test
    fun `agent help text includes parameter examples`() {
        val text = AgentHelpText.generate()

        assertTrue(text.contains("-Ppattern="))
        assertTrue(text.contains("-Pmethod="))
        assertTrue(text.contains("-Pmaxdepth="))
        assertTrue(text.contains("-Preverse=true"))
        assertTrue(text.contains("-Pincludetest=true"))
    }

    @Test
    fun `agent help text documents JSON schemas for each task`() {
        val text = AgentHelpText.generate()

        assertTrue(text.contains("className"), "Should document className field")
        assertTrue(text.contains("sourceFile"), "Should document sourceFile field")
        assertTrue(text.contains("sourcePath"), "Should document sourcePath field")
        assertTrue(text.contains("\"method\""), "Should document method field in call trees")
        assertTrue(text.contains("\"children\""), "Should document children field in call trees")
        assertTrue(text.contains("\"interface\""), "Should document interface field")
        assertTrue(text.contains("\"implementors\""), "Should document implementors field")
        assertTrue(text.contains("\"dependencies\""), "Should document dependencies field")
        assertTrue(text.contains("\"dependents\""), "Should document dependents field")
    }

    @Test
    fun `agent help text includes jq examples`() {
        val text = AgentHelpText.generate()

        assertTrue(text.contains("jq"), "Should mention jq")
        assertTrue(text.contains("| jq"), "Should show pipe to jq")
    }
}
