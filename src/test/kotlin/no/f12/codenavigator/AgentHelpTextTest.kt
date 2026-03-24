package no.f12.codenavigator

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class AgentHelpTextTest {

    @Test
    fun `Gradle agent help text contains all task names with Gradle names`() {
        val text = AgentHelpText.generate(BuildTool.GRADLE)

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

        // Usages task
        assertTrue(text.contains("cnavUsages"))

        // Analysis tasks
        assertTrue(text.contains("cnavHotspots"))
        assertTrue(text.contains("cnavCoupling"))
        assertTrue(text.contains("cnavAge"))
        assertTrue(text.contains("cnavAuthors"))
        assertTrue(text.contains("cnavChurn"))
    }

    @Test
    fun `Maven agent help text contains all task names with Maven names`() {
        val text = AgentHelpText.generate(BuildTool.MAVEN)

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
        assertTrue(text.contains("cnav:hotspots"))
        assertTrue(text.contains("cnav:coupling"))
        assertTrue(text.contains("cnav:code-age"))
        assertTrue(text.contains("cnav:authors"))
        assertTrue(text.contains("cnav:churn"))
    }

    @Test
    fun `Gradle agent help text uses -P parameters and gradlew command`() {
        val text = AgentHelpText.generate(BuildTool.GRADLE)

        assertTrue(text.contains("-Pllm=true"))
        assertTrue(text.contains("-Pformat=json"))
        assertTrue(text.contains("./gradlew"))
    }

    @Test
    fun `Maven agent help text uses -D parameters and mvn command`() {
        val text = AgentHelpText.generate(BuildTool.MAVEN)

        assertTrue(text.contains("-Dllm=true"))
        assertTrue(text.contains("-Dformat=json"))
        assertTrue(text.contains("mvn"))
        assertFalse(text.contains("./gradlew"), "Maven agent help should not contain ./gradlew")
        assertFalse(text.contains("-Pllm"), "Maven agent help should not contain -P params")
        assertFalse(text.contains("-Pformat"), "Maven agent help should not contain -P params")
    }

    @Test
    fun `agent help text includes workflow guidance`() {
        val text = AgentHelpText.generate(BuildTool.GRADLE)

        assertTrue(text.contains("ORIENT"))
        assertTrue(text.contains("FIND"))
        assertTrue(text.contains("INSPECT"))
        assertTrue(text.contains("TRACE"))
        assertTrue(text.contains("MAP"))
        assertTrue(text.contains("ANALYZE"))
    }

    @Test
    fun `agent help text includes performance tips`() {
        val text = AgentHelpText.generate(BuildTool.GRADLE)

        assertTrue(text.contains("Tips for Optimal Results"))
        assertTrue(text.contains("cached"))
    }

    @Test
    fun `Gradle agent help text includes Gradle parameter examples`() {
        val text = AgentHelpText.generate(BuildTool.GRADLE)

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
        assertTrue(text.contains("-Pdsm-depth="))
        assertTrue(text.contains("-Pdsm-html="))
    }

    @Test
    fun `Maven agent help text includes Maven parameter examples`() {
        val text = AgentHelpText.generate(BuildTool.MAVEN)

        assertTrue(text.contains("-Dpattern="))
        assertTrue(text.contains("-Dmethod="))
        assertTrue(text.contains("-Dmaxdepth="))
        assertTrue(text.contains("-Dreverse=true"))
        assertTrue(text.contains("-Dincludetest=true"))
        assertTrue(text.contains("-Dafter="))
        assertTrue(text.contains("-Dtop="))
        assertTrue(text.contains("-Dmin-revs="))
        assertTrue(text.contains("-Dmin-coupling="))
        assertTrue(text.contains("-Dno-follow"))
        assertTrue(text.contains("-Droot-package="))
        assertTrue(text.contains("-Ddsm-depth="))
        assertTrue(text.contains("-Ddsm-html="))
    }

    @Test
    fun `agent help text documents JSON schemas for each task`() {
        val text = AgentHelpText.generate(BuildTool.GRADLE)

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
        val text = AgentHelpText.generate(BuildTool.GRADLE)

        assertTrue(text.contains("jq"), "Should mention jq")
        assertTrue(text.contains("| jq"), "Should show pipe to jq")
    }

    @Test
    fun `Gradle jq examples use gradlew command`() {
        val text = AgentHelpText.generate(BuildTool.GRADLE)

        assertTrue(text.contains("./gradlew cnavListClasses"))
    }

    @Test
    fun `Maven jq examples use mvn command`() {
        val text = AgentHelpText.generate(BuildTool.MAVEN)

        assertTrue(text.contains("mvn cnav:list-classes"))
    }

    @Test
    fun `Gradle agent help text includes owner and type parameters`() {
        val text = AgentHelpText.generate(BuildTool.GRADLE)

        assertTrue(text.contains("-Powner="), "Should list owner parameter")
        assertTrue(text.contains("-Ptype="), "Should list type parameter")
    }

    @Test
    fun `Maven agent help text includes owner and type parameters`() {
        val text = AgentHelpText.generate(BuildTool.MAVEN)

        assertTrue(text.contains("-Downer="), "Should list owner parameter for Maven")
        assertTrue(text.contains("-Dtype="), "Should list type parameter for Maven")
    }

    @Test
    fun `agent help text documents JSON schema for find-usages`() {
        val text = AgentHelpText.generate(BuildTool.GRADLE)

        assertTrue(text.contains("\"targetOwner\""), "Should document targetOwner field")
        assertTrue(text.contains("\"targetMethod\""), "Should document targetMethod field")
    }

    @Test
    fun `agent help text mentions migration workflows`() {
        val text = AgentHelpText.generate(BuildTool.GRADLE)

        assertTrue(text.contains("migrat"), "Should mention migration use case")
    }

    @Test
    fun `default parameter is GRADLE for backward compatibility`() {
        val defaultText = AgentHelpText.generate()
        val gradleText = AgentHelpText.generate(BuildTool.GRADLE)

        assertTrue(defaultText == gradleText, "Default should produce same output as explicit GRADLE")
    }

    @Test
    fun `agent help text emphasizes one-shot accuracy over iterative grep`() {
        val text = AgentHelpText.generate(BuildTool.GRADLE)

        assertTrue(text.contains("single"), "Should mention getting results in a single call")
        assertTrue(text.contains("tool call"), "Should frame benefit in terms of tool call round-trips")
        assertTrue(text.contains("correct on the first"), "Should emphasize correctness on first response")
        assertTrue(text.contains("No iterative"), "Should explicitly state no iterative searching needed")
    }

    @Test
    fun `agent help text includes when-to-use guidance`() {
        val text = AgentHelpText.generate(BuildTool.GRADLE)

        assertTrue(text.contains("Use code-navigator when"), "Should have when-to-use-cnav section")
        assertTrue(text.contains("Use grep"), "Should have when-to-use-grep section")
    }

    @Test
    fun `Maven extracting output section uses mvn command`() {
        val text = AgentHelpText.generate(BuildTool.MAVEN)

        assertTrue(text.contains("mvn cnav:list-classes"), "Maven extraction example should use mvn command")
        assertFalse(text.contains("./gradlew cnavListClasses"), "Maven should not contain Gradle extraction examples")
    }
}
