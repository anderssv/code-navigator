package no.f12.codenavigator

import kotlin.test.Test
import kotlin.test.assertTrue

class ConfigHelpTextTest {

    @Test
    fun `lists all global parameters for Gradle`() {
        val text = ConfigHelpText.generate(BuildTool.GRADLE)

        assertTrue(text.contains("-Pformat=json"))
        assertTrue(text.contains("-Pllm=true"))
    }

    @Test
    fun `lists all global parameters for Maven`() {
        val text = ConfigHelpText.generate(BuildTool.MAVEN)

        assertTrue(text.contains("-Dformat=json"))
        assertTrue(text.contains("-Dllm=true"))
    }

    @Test
    fun `Gradle uses -P prefix and gradlew command`() {
        val text = ConfigHelpText.generate(BuildTool.GRADLE)

        assertTrue(text.contains("-Ppattern="))
        assertTrue(text.contains("./gradlew"))
        assertTrue(text.contains("Gradle project properties (-P flags)"))
    }

    @Test
    fun `Maven uses -D prefix and mvn command`() {
        val text = ConfigHelpText.generate(BuildTool.MAVEN)

        assertTrue(text.contains("-Dpattern="))
        assertTrue(text.contains("mvn"))
        assertTrue(text.contains("Maven system properties (-D flags)"))
    }

    @Test
    fun `Gradle lists navigation task parameters`() {
        val text = ConfigHelpText.generate(BuildTool.GRADLE)

        assertTrue(text.contains("-Ppattern="))
        assertTrue(text.contains("-Pmethod="))
        assertTrue(text.contains("-Pmaxdepth="))
        assertTrue(text.contains("-Pprojectonly="))
        assertTrue(text.contains("-Preverse="))
        assertTrue(text.contains("-Pincludetest="))
    }

    @Test
    fun `Maven lists navigation task parameters`() {
        val text = ConfigHelpText.generate(BuildTool.MAVEN)

        assertTrue(text.contains("-Dpattern="))
        assertTrue(text.contains("-Dmethod="))
        assertTrue(text.contains("-Dmaxdepth="))
        assertTrue(text.contains("-Dprojectonly="))
        assertTrue(text.contains("-Dreverse="))
        assertTrue(text.contains("-Dincludetest="))
    }

    @Test
    fun `lists DSM parameters`() {
        val text = ConfigHelpText.generate(BuildTool.GRADLE)

        assertTrue(text.contains("root-package="))
        assertTrue(text.contains("dsm-depth="))
        assertTrue(text.contains("dsm-html="))
    }

    @Test
    fun `lists git analysis parameters`() {
        val text = ConfigHelpText.generate(BuildTool.GRADLE)

        assertTrue(text.contains("after="))
        assertTrue(text.contains("top="))
        assertTrue(text.contains("min-revs="))
        assertTrue(text.contains("min-shared-revs="))
        assertTrue(text.contains("min-coupling="))
        assertTrue(text.contains("max-changeset-size="))
        assertTrue(text.contains("no-follow"))
    }

    @Test
    fun `Gradle indicates which tasks use each parameter`() {
        val text = ConfigHelpText.generate(BuildTool.GRADLE)

        assertTrue(text.contains("cnavFindClass"))
        assertTrue(text.contains("cnavCallers"))
        assertTrue(text.contains("cnavDsm"))
        assertTrue(text.contains("cnavHotspots"))
    }

    @Test
    fun `Maven indicates which tasks use each parameter with Maven names`() {
        val text = ConfigHelpText.generate(BuildTool.MAVEN)

        assertTrue(text.contains("cnav:find-class"))
        assertTrue(text.contains("cnav:find-callers"))
        assertTrue(text.contains("cnav:dsm"))
        assertTrue(text.contains("cnav:hotspots"))
    }

    @Test
    fun `includes default values where applicable`() {
        val text = ConfigHelpText.generate(BuildTool.GRADLE)

        assertTrue(text.contains("default:"), "Should show default values")
    }

    @Test
    fun `lists find-usages parameters for Gradle`() {
        val text = ConfigHelpText.generate(BuildTool.GRADLE)

        assertTrue(text.contains("-Powner="), "Should list owner parameter")
        assertTrue(text.contains("-Ptype="), "Should list type parameter")
        assertTrue(text.contains("cnavUsages"), "owner/type should reference cnavUsages task")
    }

    @Test
    fun `lists find-usages parameters for Maven`() {
        val text = ConfigHelpText.generate(BuildTool.MAVEN)

        assertTrue(text.contains("-Downer="), "Should list owner parameter for Maven")
        assertTrue(text.contains("-Dtype="), "Should list type parameter for Maven")
        assertTrue(text.contains("cnav:find-usages"), "owner/type should reference find-usages goal")
    }

    @Test
    fun `method parameter mentions find-usages task`() {
        val text = ConfigHelpText.generate(BuildTool.GRADLE)

        // method is used by find-callers, find-callees, AND find-usages
        val methodLine = text.lines().first { it.contains("-Pmethod=") }
        assertTrue(methodLine.contains("cnavUsages"), "method parameter should mention cnavUsages")
    }

    @Test
    fun `default parameter is GRADLE for backward compatibility`() {
        val defaultText = ConfigHelpText.generate()
        val gradleText = ConfigHelpText.generate(BuildTool.GRADLE)

        assertTrue(defaultText == gradleText, "Default should produce same output as explicit GRADLE")
    }
}
