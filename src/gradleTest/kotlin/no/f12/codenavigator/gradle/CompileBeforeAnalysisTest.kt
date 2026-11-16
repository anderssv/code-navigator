package no.f12.codenavigator.gradle

import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CompileBeforeAnalysisTest {

    private fun project(vararg props: Pair<String, String>): Project {
        val p = ProjectBuilder.builder().build()
        props.forEach { (k, v) -> p.extensions.extraProperties.set(k, v) }
        p.plugins.apply("java")
        p.plugins.apply(CodeNavigatorPlugin::class.java)
        return p
    }

    private fun Project.dependencyNames(task: String): Set<String> =
        tasks.getByName(task).taskDependencies.getDependencies(tasks.getByName(task)).map { it.name }.toSet()

    @Test
    fun `bytecode tasks compile first by default`() {
        val p = project()

        assertTrue("testClasses" in p.dependencyNames("cnavFindCallers"), "test callers are part of the answer: ${p.dependencyNames("cnavFindCallers")}")
        assertTrue("testClasses" in p.dependencyNames("cnavDead"), "${p.dependencyNames("cnavDead")}")
    }

    @Test
    fun `cnav_compile=false skips compilation, for code that does not compile mid-refactoring`() {
        val p = project("cnav.compile" to "false")

        assertFalse("testClasses" in p.dependencyNames("cnavFindCallers"))
    }

    @Test
    fun `source-only tasks do not compile`() {
        assertFalse("testClasses" in project().dependencyNames("cnavSize"))
    }
}
