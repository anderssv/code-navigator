package no.f12.codenavigator.navigation

import kotlin.test.Test
import kotlin.test.assertEquals

class ClassNameTest {

    @Test
    fun `wraps a fully qualified class name`() {
        val name = ClassName("com.example.MyService")

        assertEquals("com.example.MyService", name.value)
    }

    @Test
    fun `extracts package name`() {
        val name = ClassName("com.example.service.MyService")

        assertEquals(PackageName("com.example.service"), name.packageName())
    }

    @Test
    fun `extracts empty package for default package`() {
        val name = ClassName("MyService")

        assertEquals(PackageName(""), name.packageName())
    }

    @Test
    fun `toString returns the class name`() {
        val name = ClassName("com.example.MyService")

        assertEquals("com.example.MyService", name.toString())
    }

    @Test
    fun `equality is based on value`() {
        val a = ClassName("com.example.Foo")
        val b = ClassName("com.example.Foo")

        assertEquals(a, b)
    }
}

class PackageNameTest {

    @Test
    fun `wraps a package name`() {
        val pkg = PackageName("com.example.service")

        assertEquals("com.example.service", pkg.value)
    }

    @Test
    fun `toString returns the package name`() {
        val pkg = PackageName("com.example.service")

        assertEquals("com.example.service", pkg.toString())
    }

    @Test
    fun `equality is based on value`() {
        val a = PackageName("com.example")
        val b = PackageName("com.example")

        assertEquals(a, b)
    }

    @Test
    fun `empty package name`() {
        val pkg = PackageName("")

        assertEquals("", pkg.value)
    }
}
