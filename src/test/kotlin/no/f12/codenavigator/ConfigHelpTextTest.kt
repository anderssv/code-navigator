package no.f12.codenavigator

import kotlin.test.Test
import kotlin.test.assertTrue

class ConfigHelpTextTest {

    @Test
    fun `lists all global parameters`() {
        val text = ConfigHelpText.generate()

        assertTrue(text.contains("-Pformat=json"))
        assertTrue(text.contains("-Pllm=true"))
    }

    @Test
    fun `lists navigation task parameters`() {
        val text = ConfigHelpText.generate()

        assertTrue(text.contains("-Ppattern="))
        assertTrue(text.contains("-Pmethod="))
        assertTrue(text.contains("-Pmaxdepth="))
        assertTrue(text.contains("-Pprojectonly="))
        assertTrue(text.contains("-Preverse="))
        assertTrue(text.contains("-Pincludetest="))
    }

    @Test
    fun `lists DSM parameters`() {
        val text = ConfigHelpText.generate()

        assertTrue(text.contains("-Proot-package="))
        assertTrue(text.contains("-Pdepth="))
        assertTrue(text.contains("-Pdsm-html="))
    }

    @Test
    fun `lists git analysis parameters`() {
        val text = ConfigHelpText.generate()

        assertTrue(text.contains("-Pafter="))
        assertTrue(text.contains("-Ptop="))
        assertTrue(text.contains("-Pmin-revs="))
        assertTrue(text.contains("-Pmin-shared-revs="))
        assertTrue(text.contains("-Pmin-coupling="))
        assertTrue(text.contains("-Pmax-changeset-size="))
        assertTrue(text.contains("-Pno-follow"))
    }

    @Test
    fun `indicates which tasks use each parameter`() {
        val text = ConfigHelpText.generate()

        assertTrue(text.contains("cnavFindClass"))
        assertTrue(text.contains("cnavCallers"))
        assertTrue(text.contains("cnavDsm"))
        assertTrue(text.contains("cnavHotspots"))
    }

    @Test
    fun `includes default values where applicable`() {
        val text = ConfigHelpText.generate()

        assertTrue(text.contains("default:"), "Should show default values")
    }
}
