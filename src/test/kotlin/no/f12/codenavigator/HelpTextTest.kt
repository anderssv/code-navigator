package no.f12.codenavigator

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class HelpTextTest {

    @Test
    fun `Gradle help text lists all available tasks with Gradle names`() {
        val text = HelpText.generate(BuildTool.GRADLE)

        assertTrue(text.contains("cnavListClasses"))
        assertTrue(text.contains("cnavFindClass"))
        assertTrue(text.contains("cnavFindSymbol"))
        assertTrue(text.contains("cnavCallers"))
        assertTrue(text.contains("cnavCallees"))
        assertTrue(text.contains("cnavClass"))
        assertTrue(text.contains("cnavInterfaces"))
        assertTrue(text.contains("cnavDeps"))
        assertTrue(text.contains("cnavDsm"))
        assertTrue(text.contains("cnavUsages"))
        assertTrue(text.contains("cnavHelp"))
        assertTrue(text.contains("cnavAgentHelp"))
        assertTrue(text.contains("cnavHelpConfig"))
        assertTrue(text.contains("cnavHotspots"))
        assertTrue(text.contains("cnavCoupling"))
        assertTrue(text.contains("cnavAge"))
        assertTrue(text.contains("cnavAuthors"))
        assertTrue(text.contains("cnavChurn"))
    }

    @Test
    fun `Maven help text lists all available tasks with Maven names`() {
        val text = HelpText.generate(BuildTool.MAVEN)

        assertTrue(text.contains("cnav:list-classes"))
        assertTrue(text.contains("cnav:find-class"))
        assertTrue(text.contains("cnav:find-symbol"))
        assertTrue(text.contains("cnav:find-callers"))
        assertTrue(text.contains("cnav:find-callees"))
        assertTrue(text.contains("cnav:class-detail"))
        assertTrue(text.contains("cnav:find-interfaces"))
        assertTrue(text.contains("cnav:package-deps"))
        assertTrue(text.contains("cnav:dsm"))
        assertTrue(text.contains("cnav:find-usages"))
        assertTrue(text.contains("cnav:help"))
        assertTrue(text.contains("cnav:agent-help"))
        assertTrue(text.contains("cnav:config-help"))
        assertTrue(text.contains("cnav:hotspots"))
        assertTrue(text.contains("cnav:coupling"))
        assertTrue(text.contains("cnav:code-age"))
        assertTrue(text.contains("cnav:authors"))
        assertTrue(text.contains("cnav:churn"))
    }

    @Test
    fun `Gradle help text uses -P parameters and gradlew command`() {
        val text = HelpText.generate(BuildTool.GRADLE)

        assertTrue(text.contains("-Ppattern="))
        assertTrue(text.contains("./gradlew"))
    }

    @Test
    fun `Maven help text uses -D parameters and mvn command`() {
        val text = HelpText.generate(BuildTool.MAVEN)

        assertTrue(text.contains("-Dpattern="))
        assertTrue(text.contains("mvn"))
        assertFalse(text.contains("./gradlew"), "Maven help should not contain ./gradlew")
        assertFalse(text.contains("-Ppattern"), "Maven help should not contain -P params")
    }

    @Test
    fun `help text includes usage examples`() {
        val gradleText = HelpText.generate(BuildTool.GRADLE)
        val mavenText = HelpText.generate(BuildTool.MAVEN)

        assertTrue(gradleText.contains("./gradlew cnavFindClass -Ppattern=Service"))
        assertTrue(mavenText.contains("mvn cnav:find-class -Dpattern=Service"))
    }

    @Test
    fun `default parameter is GRADLE for backward compatibility`() {
        val defaultText = HelpText.generate()
        val gradleText = HelpText.generate(BuildTool.GRADLE)

        assertTrue(defaultText == gradleText, "Default should produce same output as explicit GRADLE")
    }
}
