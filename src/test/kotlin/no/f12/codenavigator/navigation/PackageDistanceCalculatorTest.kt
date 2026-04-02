package no.f12.codenavigator.navigation

import no.f12.codenavigator.navigation.core.PackageName
import no.f12.codenavigator.navigation.dsm.PackageDistanceCalculator
import kotlin.test.Test
import kotlin.test.assertEquals

class PackageDistanceCalculatorTest {

    @Test
    fun `same package has distance zero`() {
        val distance = PackageDistanceCalculator.distance(
            PackageName("com.example.api"),
            PackageName("com.example.api"),
        )

        assertEquals(0, distance)
    }
    @Test
    fun `sibling packages have distance two`() {
        val distance = PackageDistanceCalculator.distance(
            PackageName("com.example.api"),
            PackageName("com.example.model"),
        )

        assertEquals(2, distance)
    }
    @Test
    fun `parent-child packages have distance one`() {
        val distance = PackageDistanceCalculator.distance(
            PackageName("com.example.order"),
            PackageName("com.example.order.validation"),
        )

        assertEquals(1, distance)
    }

    @Test
    fun `grandparent-grandchild packages have distance two`() {
        val distance = PackageDistanceCalculator.distance(
            PackageName("com.example"),
            PackageName("com.example.order.validation"),
        )

        assertEquals(2, distance)
    }

    @Test
    fun `packages in completely different trees have distance equal to total segments`() {
        val distance = PackageDistanceCalculator.distance(
            PackageName("com.foo.api"),
            PackageName("org.bar.service"),
        )

        assertEquals(6, distance)
    }

    @Test
    fun `empty package to non-empty package`() {
        val distance = PackageDistanceCalculator.distance(
            PackageName(""),
            PackageName("com.example.api"),
        )

        assertEquals(3, distance)
    }

    @Test
    fun `both packages empty has distance zero`() {
        val distance = PackageDistanceCalculator.distance(
            PackageName(""),
            PackageName(""),
        )

        assertEquals(0, distance)
    }

    @Test
    fun `single-segment packages that differ have distance two`() {
        val distance = PackageDistanceCalculator.distance(
            PackageName("api"),
            PackageName("model"),
        )

        assertEquals(2, distance)
    }

    @Test
    fun `deep packages with long common prefix`() {
        val distance = PackageDistanceCalculator.distance(
            PackageName("com.example.app.order.api.rest.v1"),
            PackageName("com.example.app.order.persistence.jpa"),
        )

        assertEquals(5, distance)
    }
}
