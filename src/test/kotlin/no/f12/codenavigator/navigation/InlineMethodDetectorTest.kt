package no.f12.codenavigator.navigation

import no.f12.codenavigator.navigation.core.ClassName
import no.f12.codenavigator.navigation.deadcode.InlineMethodDetector
import no.f12.codenavigator.navigation.fixtures.ClassWithInline
import no.f12.codenavigator.navigation.fixtures.ClassWithMixedMethods
import no.f12.codenavigator.navigation.fixtures.ClassWithoutInline
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InlineMethodDetectorTest {

    private val testClassesDir: File
        get() {
            val location = ClassWithInline::class.java.protectionDomain.codeSource.location
            return File(location.toURI())
        }

    @Test
    fun `detects inline method in class`() {
        val inlineMethods = InlineMethodDetector.scanAll(listOf(testClassesDir))

        val classWithInline = ClassName("no.f12.codenavigator.navigation.fixtures.ClassWithInline")
        val methods = inlineMethods.filter { it.className == classWithInline }.map { it.methodName }
        assertTrue("inlineMethod" in methods, "inlineMethod should be detected as inline")
    }

    @Test
    fun `does not flag normal methods as inline`() {
        val inlineMethods = InlineMethodDetector.scanAll(listOf(testClassesDir))

        val classWithInline = ClassName("no.f12.codenavigator.navigation.fixtures.ClassWithInline")
        val methods = inlineMethods.filter { it.className == classWithInline }.map { it.methodName }
        assertTrue("normalMethod" !in methods, "normalMethod should not be detected as inline")
    }

    @Test
    fun `class without inline methods returns no results`() {
        val inlineMethods = InlineMethodDetector.scanAll(listOf(testClassesDir))

        val classWithoutInline = ClassName("no.f12.codenavigator.navigation.fixtures.ClassWithoutInline")
        val methods = inlineMethods.filter { it.className == classWithoutInline }
        assertTrue(methods.isEmpty(), "ClassWithoutInline should have no inline methods: $methods")
    }

    @Test
    fun `mixed class detects only inline methods`() {
        val inlineMethods = InlineMethodDetector.scanAll(listOf(testClassesDir))

        val mixed = ClassName("no.f12.codenavigator.navigation.fixtures.ClassWithMixedMethods")
        val methods = inlineMethods.filter { it.className == mixed }.map { it.methodName }.toSet()
        assertEquals(setOf("inlineReified", "inlineSimple"), methods, "Only inline methods should be detected")
    }

    @Test
    fun `returns empty set for empty directory list`() {
        val inlineMethods = InlineMethodDetector.scanAll(emptyList())

        assertTrue(inlineMethods.isEmpty())
    }
}
