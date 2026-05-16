package no.f12.codenavigator.navigation.dsm

import no.f12.codenavigator.navigation.*

import no.f12.codenavigator.navigation.types.ClassName
import no.f12.codenavigator.navigation.types.PackageName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WhyDependsBuilderTest {

    // [TEST] Empty dependency list produces empty result
    // [TEST] Single class-level edge from→to is included
    // [TEST] Multiple edges from→to are grouped by source class
    // [TEST] Edges in the opposite direction (to→from) are excluded
    // [TEST] Edges between unrelated packages are excluded
    // [TEST] Sub-packages of "from" are included
    // [TEST] Sub-packages of "to" are included
    // [TEST] Multiple source classes referencing same target class
    // [TEST] Multiple target classes from same source class

    @Test
    fun `empty dependency list produces empty result`() {
        val result = WhyDependsBuilder.build(
            dependencies = emptyList(),
            fromPackage = PackageName("com.example.api"),
            toPackage = PackageName("com.example.db"),
        )

        assertTrue(result.edges.isEmpty())
    }

    @Test
    fun `single class-level edge from to is included`() {
        val deps = listOf(
            PackageDependency(PackageName("com.example.api"), PackageName("com.example.db"), ClassName("com.example.api.Controller"), ClassName("com.example.db.Repo")),
        )

        val result = WhyDependsBuilder.build(deps, PackageName("com.example.api"), PackageName("com.example.db"))

        assertEquals(1, result.edges.size)
        assertEquals(ClassName("com.example.api.Controller"), result.edges[0].sourceClass)
        assertEquals(ClassName("com.example.db.Repo"), result.edges[0].targetClass)
    }

    @Test
    fun `edges in the opposite direction are excluded`() {
        val deps = listOf(
            PackageDependency(PackageName("com.example.db"), PackageName("com.example.api"), ClassName("com.example.db.Repo"), ClassName("com.example.api.Controller")),
        )

        val result = WhyDependsBuilder.build(deps, PackageName("com.example.api"), PackageName("com.example.db"))

        assertTrue(result.edges.isEmpty())
    }

    @Test
    fun `edges between unrelated packages are excluded`() {
        val deps = listOf(
            PackageDependency(PackageName("com.other.x"), PackageName("com.other.y"), ClassName("com.other.x.Foo"), ClassName("com.other.y.Bar")),
        )

        val result = WhyDependsBuilder.build(deps, PackageName("com.example.api"), PackageName("com.example.db"))

        assertTrue(result.edges.isEmpty())
    }

    @Test
    fun `sub-packages of from are included`() {
        val deps = listOf(
            PackageDependency(PackageName("com.example.api.v2"), PackageName("com.example.db"), ClassName("com.example.api.v2.Controller"), ClassName("com.example.db.Repo")),
        )

        val result = WhyDependsBuilder.build(deps, PackageName("com.example.api"), PackageName("com.example.db"))

        assertEquals(1, result.edges.size)
    }

    @Test
    fun `sub-packages of to are included`() {
        val deps = listOf(
            PackageDependency(PackageName("com.example.api"), PackageName("com.example.db.sql"), ClassName("com.example.api.Controller"), ClassName("com.example.db.sql.SqlRepo")),
        )

        val result = WhyDependsBuilder.build(deps, PackageName("com.example.api"), PackageName("com.example.db"))

        assertEquals(1, result.edges.size)
    }

    @Test
    fun `multiple source classes referencing same target class`() {
        val deps = listOf(
            PackageDependency(PackageName("com.example.api"), PackageName("com.example.db"), ClassName("com.example.api.Controller"), ClassName("com.example.db.Repo")),
            PackageDependency(PackageName("com.example.api"), PackageName("com.example.db"), ClassName("com.example.api.Handler"), ClassName("com.example.db.Repo")),
        )

        val result = WhyDependsBuilder.build(deps, PackageName("com.example.api"), PackageName("com.example.db"))

        assertEquals(2, result.edges.size)
        val sources = result.edges.map { it.sourceClass.value }.toSet()
        assertEquals(setOf("com.example.api.Controller", "com.example.api.Handler"), sources)
    }

    @Test
    fun `multiple target classes from same source class`() {
        val deps = listOf(
            PackageDependency(PackageName("com.example.api"), PackageName("com.example.db"), ClassName("com.example.api.Controller"), ClassName("com.example.db.Repo")),
            PackageDependency(PackageName("com.example.api"), PackageName("com.example.db"), ClassName("com.example.api.Controller"), ClassName("com.example.db.Connection")),
        )

        val result = WhyDependsBuilder.build(deps, PackageName("com.example.api"), PackageName("com.example.db"))

        assertEquals(2, result.edges.size)
        val targets = result.edges.map { it.targetClass.value }.toSet()
        assertEquals(setOf("com.example.db.Repo", "com.example.db.Connection"), targets)
    }

    @Test
    fun `duplicate edges are counted`() {
        val deps = listOf(
            PackageDependency(PackageName("com.example.api"), PackageName("com.example.db"), ClassName("com.example.api.Controller"), ClassName("com.example.db.Repo")),
            PackageDependency(PackageName("com.example.api"), PackageName("com.example.db"), ClassName("com.example.api.Controller"), ClassName("com.example.db.Repo")),
        )

        val result = WhyDependsBuilder.build(deps, PackageName("com.example.api"), PackageName("com.example.db"))

        assertEquals(1, result.edges.size)
        assertEquals(2, result.edges[0].count)
    }
}
