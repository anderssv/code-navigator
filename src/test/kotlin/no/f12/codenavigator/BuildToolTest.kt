package no.f12.codenavigator

import no.f12.codenavigator.registry.BuildTool
import no.f12.codenavigator.registry.TaskDef
import kotlin.test.Test
import kotlin.test.assertEquals

class BuildToolTest {

    @Test
    fun `GRADLE taskName maps goal to Gradle task name`() {
        assertEquals("cnavListClasses", BuildTool.GRADLE.taskName("list-classes"))
        assertEquals("cnavFindClass", BuildTool.GRADLE.taskName("find-class"))
        assertEquals("cnavFindSymbol", BuildTool.GRADLE.taskName("find-symbol"))
        assertEquals("cnavClassDetail", BuildTool.GRADLE.taskName("class-detail"))
        assertEquals("cnavFindCallers", BuildTool.GRADLE.taskName("find-callers"))
        assertEquals("cnavFindCallees", BuildTool.GRADLE.taskName("find-callees"))
        assertEquals("cnavFindInterfaces", BuildTool.GRADLE.taskName("find-interfaces"))
        assertEquals("cnavPackageDeps", BuildTool.GRADLE.taskName("package-deps"))
        assertEquals("cnavDsm", BuildTool.GRADLE.taskName("dsm"))
        assertEquals("cnavHotspots", BuildTool.GRADLE.taskName("hotspots"))
        assertEquals("cnavChurn", BuildTool.GRADLE.taskName("churn"))
        assertEquals("cnavCodeAge", BuildTool.GRADLE.taskName("code-age"))
        assertEquals("cnavAuthors", BuildTool.GRADLE.taskName("authors"))
        assertEquals("cnavCoupling", BuildTool.GRADLE.taskName("coupling"))
        assertEquals("cnavHelp", BuildTool.GRADLE.taskName("help"))
        assertEquals("cnavAgentHelp", BuildTool.GRADLE.taskName("agent-help"))
        assertEquals("cnavConfigHelp", BuildTool.GRADLE.taskName("config-help"))
    }

    @Test
    fun `MAVEN taskName returns kebab-case with cnav prefix`() {
        assertEquals("cnav:list-classes", BuildTool.MAVEN.taskName("list-classes"))
        assertEquals("cnav:find-class", BuildTool.MAVEN.taskName("find-class"))
        assertEquals("cnav:class-detail", BuildTool.MAVEN.taskName("class-detail"))
        assertEquals("cnav:find-callers", BuildTool.MAVEN.taskName("find-callers"))
        assertEquals("cnav:dsm", BuildTool.MAVEN.taskName("dsm"))
        assertEquals("cnav:help", BuildTool.MAVEN.taskName("help"))
    }

    @Test
    fun `GRADLE param returns -P prefix`() {
        assertEquals("-Ppattern=Service", BuildTool.GRADLE.param("pattern", "Service"))
    }

    @Test
    fun `MAVEN param returns -D prefix`() {
        assertEquals("-Dpattern=Service", BuildTool.MAVEN.param("pattern", "Service"))
    }

    @Test
    fun `GRADLE param flag returns -P prefix without value`() {
        assertEquals("-Pno-follow", BuildTool.GRADLE.paramFlag("no-follow"))
    }

    @Test
    fun `MAVEN param flag returns -D prefix without value`() {
        assertEquals("-Dno-follow", BuildTool.MAVEN.paramFlag("no-follow"))
    }

    @Test
    fun `GRADLE command returns gradlew prefix`() {
        assertEquals("./gradlew", BuildTool.GRADLE.command)
    }

    @Test
    fun `MAVEN command returns mvn prefix`() {
        assertEquals("mvn", BuildTool.MAVEN.command)
    }

    @Test
    fun `GRADLE usage formats full command with task and params`() {
        val result = BuildTool.GRADLE.usage("find-class", "-Ppattern=Service")

        assertEquals("./gradlew cnavFindClass -Ppattern=Service", result)
    }

    @Test
    fun `MAVEN usage formats full command with task and params`() {
        val result = BuildTool.MAVEN.usage("find-class", "-Dpattern=Service")

        assertEquals("mvn cnav:find-class -Dpattern=Service", result)
    }

    @Test
    fun `usage with no params`() {
        assertEquals("./gradlew cnavListClasses", BuildTool.GRADLE.usage("list-classes"))
        assertEquals("mvn cnav:list-classes", BuildTool.MAVEN.usage("list-classes"))
    }

    @Test
    fun `GRADLE taskName derives name for any goal string`() {
        assertEquals("cnavSomethingNew", BuildTool.GRADLE.taskName("something-new"))
    }
}
