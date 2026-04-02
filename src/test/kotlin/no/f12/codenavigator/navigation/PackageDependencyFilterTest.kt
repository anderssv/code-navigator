package no.f12.codenavigator.navigation

import no.f12.codenavigator.navigation.dsm.PackageDependency
import no.f12.codenavigator.navigation.dsm.filterByPackage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PackageDependencyFilterTest {

    private val apiToDomain = PackageDependency(
        PackageName("com.app.api"), PackageName("com.app.domain"),
        ClassName("com.app.api.Controller"), ClassName("com.app.domain.Order"),
    )
    private val domainToApi = PackageDependency(
        PackageName("com.app.domain"), PackageName("com.app.api"),
        ClassName("com.app.domain.Factory"), ClassName("com.app.api.Utils"),
    )
    private val serviceToPersistence = PackageDependency(
        PackageName("com.app.service"), PackageName("com.app.persistence"),
        ClassName("com.app.service.OrderService"), ClassName("com.app.persistence.Repo"),
    )

    private val allDeps = listOf(apiToDomain, domainToApi, serviceToPersistence)

    @Test
    fun `null package filter returns all dependencies`() {
        val result = allDeps.filterByPackage(null)

        assertEquals(3, result.size)
    }

    @Test
    fun `empty package filter returns all dependencies`() {
        val result = allDeps.filterByPackage(PackageName(""))

        assertEquals(3, result.size)
    }

    @Test
    fun `filter matches dependencies where source starts with filter`() {
        val result = allDeps.filterByPackage(PackageName("com.app.api"))

        assertTrue(result.contains(apiToDomain))
    }

    @Test
    fun `filter matches dependencies where target starts with filter`() {
        val result = allDeps.filterByPackage(PackageName("com.app.api"))

        assertTrue(result.contains(domainToApi))
    }

    @Test
    fun `filter excludes dependencies where neither source nor target matches`() {
        val result = allDeps.filterByPackage(PackageName("com.app.api"))

        assertEquals(2, result.size)
        assertTrue(!result.contains(serviceToPersistence))
    }

    @Test
    fun `filter with exact package match includes that package`() {
        val result = allDeps.filterByPackage(PackageName("com.app.service"))

        assertEquals(1, result.size)
        assertEquals(serviceToPersistence, result.first())
    }
}
