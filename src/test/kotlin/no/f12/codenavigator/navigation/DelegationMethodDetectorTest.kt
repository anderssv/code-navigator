package no.f12.codenavigator.navigation

import no.f12.codenavigator.navigation.core.ClassName
import no.f12.codenavigator.navigation.deadcode.DelegationMethodDetector
import no.f12.codenavigator.navigation.fixtures.ClassWithDelegation
import no.f12.codenavigator.navigation.fixtures.ClassWithoutDelegation
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class DelegationMethodDetectorTest {

    private val testClassesDir: File
        get() {
            val location = ClassWithDelegation::class.java.protectionDomain.codeSource.location
            return File(location.toURI())
        }

    @Test
    fun `detects delegation methods in class with Map delegation`() {
        val delegationMethods = DelegationMethodDetector.scanAll(listOf(testClassesDir))

        val className = ClassName("no.f12.codenavigator.navigation.fixtures.ClassWithDelegation")
        val methods = delegationMethods.filter { it.className == className }.map { it.methodName }.toSet()
        assertTrue("clear" in methods, "clear() should be detected as delegation method, found: $methods")
        assertTrue("put" in methods, "put() should be detected as delegation method, found: $methods")
        assertTrue("remove" in methods, "remove() should be detected as delegation method, found: $methods")
    }

    @Test
    fun `does not flag explicit methods as delegation`() {
        val delegationMethods = DelegationMethodDetector.scanAll(listOf(testClassesDir))

        val className = ClassName("no.f12.codenavigator.navigation.fixtures.ClassWithDelegation")
        val methods = delegationMethods.filter { it.className == className }.map { it.methodName }
        assertTrue("explicitMethod" !in methods, "explicitMethod should not be detected as delegation")
    }

    @Test
    fun `class without delegation returns no results`() {
        val delegationMethods = DelegationMethodDetector.scanAll(listOf(testClassesDir))

        val className = ClassName("no.f12.codenavigator.navigation.fixtures.ClassWithoutDelegation")
        val methods = delegationMethods.filter { it.className == className }
        assertTrue(methods.isEmpty(), "ClassWithoutDelegation should have no delegation methods: $methods")
    }

    @Test
    fun `returns empty set for empty directory list`() {
        val delegationMethods = DelegationMethodDetector.scanAll(emptyList())

        assertTrue(delegationMethods.isEmpty())
    }
}
