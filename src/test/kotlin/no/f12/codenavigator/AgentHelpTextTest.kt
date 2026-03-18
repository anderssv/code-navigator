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
        assertTrue(text.contains("-Pdepth="))
        assertTrue(text.contains("-Preverse=true"))
        assertTrue(text.contains("-Pincludetest=true"))
    }
}
