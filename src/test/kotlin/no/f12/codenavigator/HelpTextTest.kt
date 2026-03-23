package no.f12.codenavigator

import kotlin.test.Test
import kotlin.test.assertTrue

class HelpTextTest {

    @Test
    fun `help text lists all available tasks`() {
        val text = HelpText.generate()

        assertTrue(text.contains("cnavListClasses"))
        assertTrue(text.contains("cnavFindClass"))
        assertTrue(text.contains("cnavFindSymbol"))
        assertTrue(text.contains("cnavCallers"))
        assertTrue(text.contains("cnavCallees"))
        assertTrue(text.contains("cnavClass"))
        assertTrue(text.contains("cnavInterfaces"))
        assertTrue(text.contains("cnavDeps"))
        assertTrue(text.contains("cnavDsm"))
        assertTrue(text.contains("cnavHelp"))
        assertTrue(text.contains("cnavHelpConfig"))
        assertTrue(text.contains("cnavHotspots"))
        assertTrue(text.contains("cnavCoupling"))
        assertTrue(text.contains("cnavAge"))
        assertTrue(text.contains("cnavAuthors"))
        assertTrue(text.contains("cnavChurn"))
    }

    @Test
    fun `help text documents the pattern parameter for findClass`() {
        val text = HelpText.generate()

        assertTrue(text.contains("-Ppattern="))
    }

    @Test
    fun `help text includes usage examples`() {
        val text = HelpText.generate()

        assertTrue(text.contains("./gradlew"))
    }
}
