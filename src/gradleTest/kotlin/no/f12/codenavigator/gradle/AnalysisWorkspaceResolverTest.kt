package no.f12.codenavigator.gradle

import no.f12.codenavigator.navigation.types.ModuleId
import no.f12.codenavigator.navigation.types.ModuleRole
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AnalysisWorkspaceResolverTest {

    @Test
    fun `single module resolution creates a one-node workspace`() {
        val root = ProjectBuilder.builder().build()
        val service = ProjectBuilder.builder().withParent(root).withName("service").build()
        applyJava(service)

        val workspace = AnalysisWorkspaceResolver.resolve(service)

        assertEquals(listOf(ModuleId(":service")), workspace.modules.map { it.id })
        assertEquals(ModuleRole.SOURCE, workspace.modules.single().role)
        assertFalse(workspace.moduleAware)
    }

    @Test
    fun `multi module resolution retains real project dependency and excludes unrelated sibling`() {
        val root = ProjectBuilder.builder().build()
        val shared = ProjectBuilder.builder().withParent(root).withName("shared").build()
        val service = ProjectBuilder.builder().withParent(root).withName("service").build()
        val unrelated = ProjectBuilder.builder().withParent(root).withName("unrelated").build()
        applyJava(shared, service, unrelated)
        dependOn(service, shared)

        val workspace = AnalysisWorkspaceResolver.resolve(service)

        assertEquals(setOf(ModuleId(":service"), ModuleId(":shared")), workspace.modules.map { it.id }.toSet())
        assertEquals(ModuleRole.SOURCE, workspace.module(ModuleId(":service"))?.role)
        assertEquals(ModuleRole.DEPENDENCY, workspace.module(ModuleId(":shared"))?.role)
        assertEquals(setOf(workspace.module(ModuleId(":shared"))), workspace.dependenciesOf(ModuleId(":service")))
        assertTrue(workspace.moduleAware)
    }

    @Test
    fun `multi module resolution retains hierarchy parent for included child`() {
        val root = ProjectBuilder.builder().build()
        val services = ProjectBuilder.builder().withParent(root).withName("services").build()
        val billing = ProjectBuilder.builder().withParent(services).withName("billing").build()
        applyJava(billing)

        val workspace = AnalysisWorkspaceResolver.resolve(services)

        assertEquals(ModuleId(":services"), workspace.module(ModuleId(":services:billing"))?.parentId)
    }

    private fun applyJava(vararg projects: Project) {
        projects.forEach { it.plugins.apply("java") }
    }

    private fun dependOn(from: Project, to: Project) {
        from.dependencies.add("implementation", from.dependencies.project(mapOf("path" to to.path)))
    }
}
