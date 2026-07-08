package no.f12.codenavigator.gradle

import org.gradle.testfixtures.ProjectBuilder
import kotlin.test.Test
import kotlin.test.assertEquals

class MultiModuleResolverTest {

    // === classify ===

    @Test
    fun `classify on a leaf with no dependencies marks itself SOURCE and everything else HIERARCHY`() {
        val root = ProjectBuilder.builder().build()
        val service = ProjectBuilder.builder().withParent(root).withName("service").build()
        ProjectBuilder.builder().withParent(root).withName("unrelated").build()

        val result = MultiModuleResolver.classify(service)

        assertEquals(ModuleRelationship.SOURCE, result[service])
        assertEquals(ModuleRelationship.HIERARCHY, result[root])
        assertEquals(ModuleRelationship.HIERARCHY, result[byPath(root, ":unrelated")])
    }

    @Test
    fun `classify on the root marks the whole subtree SOURCE`() {
        val root = ProjectBuilder.builder().build()
        val shared = ProjectBuilder.builder().withParent(root).withName("shared").build()
        val service = ProjectBuilder.builder().withParent(root).withName("service").build()

        val result = MultiModuleResolver.classify(root)

        assertEquals(ModuleRelationship.SOURCE, result[root])
        assertEquals(ModuleRelationship.SOURCE, result[shared])
        assertEquals(ModuleRelationship.SOURCE, result[service])
    }

    @Test
    fun `classify marks a real project dependency as DEPENDENCY, not HIERARCHY`() {
        val root = ProjectBuilder.builder().build()
        val shared = ProjectBuilder.builder().withParent(root).withName("shared").build()
        val service = ProjectBuilder.builder().withParent(root).withName("service").build()
        ProjectBuilder.builder().withParent(root).withName("unrelated").build()

        applyJava(shared, service)
        dependOn(service, shared)

        val result = MultiModuleResolver.classify(service)

        assertEquals(ModuleRelationship.SOURCE, result[service])
        assertEquals(ModuleRelationship.DEPENDENCY, result[shared])
        assertEquals(ModuleRelationship.HIERARCHY, result[byPath(root, ":unrelated")])
    }

    @Test
    fun `classify walks transitive project dependencies`() {
        val root = ProjectBuilder.builder().build()
        val core = ProjectBuilder.builder().withParent(root).withName("core").build()
        val shared = ProjectBuilder.builder().withParent(root).withName("shared").build()
        val service = ProjectBuilder.builder().withParent(root).withName("service").build()

        applyJava(core, shared, service)
        dependOn(shared, core)
        dependOn(service, shared)

        val result = MultiModuleResolver.classify(service)

        assertEquals(ModuleRelationship.DEPENDENCY, result[shared])
        assertEquals(ModuleRelationship.DEPENDENCY, result[core], "Transitive dependency should also be classified DEPENDENCY")
    }

    // === resolve ===

    @Test
    fun `resolve tags class directories with the source module's own path`() {
        val root = ProjectBuilder.builder().build()
        val service = ProjectBuilder.builder().withParent(root).withName("service").build()
        applyJava(service)

        val result = MultiModuleResolver.resolve(service)

        val modules = result.map { (_, mss) -> mss.moduleName }.toSet()
        assertEquals(setOf(":service"), modules)
    }

    @Test
    fun `resolve excludes HIERARCHY modules that are not real dependencies`() {
        val root = ProjectBuilder.builder().build()
        val service = ProjectBuilder.builder().withParent(root).withName("service").build()
        val unrelated = ProjectBuilder.builder().withParent(root).withName("unrelated").build()
        applyJava(service, unrelated)

        val result = MultiModuleResolver.resolve(service)

        val modules = result.map { (_, mss) -> mss.moduleName }.toSet()
        assertEquals(setOf(":service"), modules, "Unrelated sibling must not be aggregated")
    }

    @Test
    fun `resolve includes real project dependencies alongside the source module`() {
        val root = ProjectBuilder.builder().build()
        val shared = ProjectBuilder.builder().withParent(root).withName("shared").build()
        val service = ProjectBuilder.builder().withParent(root).withName("service").build()
        applyJava(shared, service)
        dependOn(service, shared)

        val result = MultiModuleResolver.resolve(service)

        val modules = result.map { (_, mss) -> mss.moduleName }.toSet()
        assertEquals(setOf(":service", ":shared"), modules)
    }

    @Test
    fun `resolve on root aggregates the whole subtree`() {
        val root = ProjectBuilder.builder().build()
        val shared = ProjectBuilder.builder().withParent(root).withName("shared").build()
        val service = ProjectBuilder.builder().withParent(root).withName("service").build()
        applyJava(root, shared, service)

        val result = MultiModuleResolver.resolve(root)

        val modules = result.map { (_, mss) -> mss.moduleName }.toSet()
        assertEquals(setOf(":", ":shared", ":service"), modules)
    }

    private fun applyJava(vararg projects: org.gradle.api.Project) {
        projects.forEach { it.plugins.apply("java") }
    }

    private fun dependOn(from: org.gradle.api.Project, to: org.gradle.api.Project) {
        from.dependencies.add("implementation", from.dependencies.project(mapOf("path" to to.path)))
    }

    private fun byPath(root: org.gradle.api.Project, path: String): org.gradle.api.Project = root.project(path)
}
