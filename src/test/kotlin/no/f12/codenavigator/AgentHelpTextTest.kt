package no.f12.codenavigator

import kotlin.test.Test
import kotlin.test.assertTrue

class AgentHelpTextTest {

    @Test
    fun `agent help text contains all task names`() {
        val text = AgentHelpText.generate()

        // Navigation tasks
        assertTrue(text.contains("cnavListClasses"))
        assertTrue(text.contains("cnavFindClass"))
        assertTrue(text.contains("cnavFindSymbol"))
        assertTrue(text.contains("cnavCallers"))
        assertTrue(text.contains("cnavCallees"))
        assertTrue(text.contains("cnavClass"))
        assertTrue(text.contains("cnavInterfaces"))
        assertTrue(text.contains("cnavDeps"))
        assertTrue(text.contains("cnavDsm"))

        // Analysis tasks
        assertTrue(text.contains("cnavHotspots"))
        assertTrue(text.contains("cnavCoupling"))
        assertTrue(text.contains("cnavAge"))
        assertTrue(text.contains("cnavAuthors"))
        assertTrue(text.contains("cnavChurn"))
    }

    @Test
    fun `agent help text recommends LLM format for agents`() {
        val text = AgentHelpText.generate()

        assertTrue(text.contains("-Pllm=true"))
        assertTrue(text.contains("-Pformat=json"))
    }

    @Test
    fun `agent help text includes workflow guidance`() {
        val text = AgentHelpText.generate()

        assertTrue(text.contains("ORIENT"))
        assertTrue(text.contains("FIND"))
        assertTrue(text.contains("INSPECT"))
        assertTrue(text.contains("TRACE"))
        assertTrue(text.contains("MAP"))
        assertTrue(text.contains("ANALYZE"))
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
        assertTrue(text.contains("-Pafter="))
        assertTrue(text.contains("-Ptop="))
        assertTrue(text.contains("-Pmin-revs="))
        assertTrue(text.contains("-Pmin-coupling="))
        assertTrue(text.contains("-Pno-follow"))
        assertTrue(text.contains("-Proot-package="))
        assertTrue(text.contains("-Pdepth="))
        assertTrue(text.contains("-Pdsm-html="))
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
