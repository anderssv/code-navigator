package no.f12.codenavigator.navigation

import kotlin.test.Test
import kotlin.test.assertEquals

class RootPackageDetectorTest {

    // [TEST] Empty list of package names returns empty package
    @Test
    fun `single package returns that package`() {
        val result = RootPackageDetector.detect(listOf(PackageName("com.example.service")))

        assertEquals(PackageName("com.example.service"), result)
    }
    @Test
    fun `two classes in same package return that package`() {
        val result = RootPackageDetector.detect(
            listOf(PackageName("com.example.service"), PackageName("com.example.service")),
        )

        assertEquals(PackageName("com.example.service"), result)
    }

    @Test
    fun `two packages with common prefix return the common prefix`() {
        val result = RootPackageDetector.detect(
            listOf(PackageName("com.example.api"), PackageName("com.example.model")),
        )

        assertEquals(PackageName("com.example"), result)
    }

    @Test
    fun `two packages with no common prefix return empty`() {
        val result = RootPackageDetector.detect(
            listOf(PackageName("com.example.api"), PackageName("org.other.model")),
        )

        assertEquals(PackageName(""), result)
    }

    @Test
    fun `one package is a prefix of another returns the shorter one`() {
        val result = RootPackageDetector.detect(
            listOf(PackageName("com.example"), PackageName("com.example.service")),
        )

        assertEquals(PackageName("com.example"), result)
    }

    @Test
    fun `ignores empty package names`() {
        val result = RootPackageDetector.detect(
            listOf(PackageName(""), PackageName("com.example.service"), PackageName("com.example.model")),
        )

        assertEquals(PackageName("com.example"), result)
    }

    @Test
    fun `multiple packages across many levels with deep common prefix`() {
        val result = RootPackageDetector.detect(
            listOf(
                PackageName("com.example.app.api.rest"),
                PackageName("com.example.app.api.grpc"),
                PackageName("com.example.app.domain.model"),
                PackageName("com.example.app.domain.service"),
            ),
        )

        assertEquals(PackageName("com.example.app"), result)
    }

    @Test
    fun `all identical packages return that package`() {
        val result = RootPackageDetector.detect(
            listOf(
                PackageName("com.example.service"),
                PackageName("com.example.service"),
                PackageName("com.example.service"),
            ),
        )

        assertEquals(PackageName("com.example.service"), result)
    }

    @Test
    fun `empty list of package names returns empty package`() {
        val result = RootPackageDetector.detect(emptyList())

        assertEquals(PackageName(""), result)
    }

    @Test
    fun `detectFromClassNames extracts packages and finds common prefix`() {
        val classNames = listOf(
            ClassName("com.example.api.UserController"),
            ClassName("com.example.model.User"),
            ClassName("com.example.service.UserService"),
        )

        val result = RootPackageDetector.detectFromClassNames(classNames)

        assertEquals(PackageName("com.example"), result)
    }

    @Test
    fun `detectFromClassNames deduplicates packages`() {
        val classNames = listOf(
            ClassName("com.example.service.ServiceA"),
            ClassName("com.example.service.ServiceB"),
            ClassName("com.example.model.Model"),
        )

        val result = RootPackageDetector.detectFromClassNames(classNames)

        assertEquals(PackageName("com.example"), result)
    }
}
