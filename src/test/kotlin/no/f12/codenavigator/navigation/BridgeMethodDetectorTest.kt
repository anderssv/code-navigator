package no.f12.codenavigator.navigation

import no.f12.codenavigator.navigation.deadcode.BridgeMethodDetector
import no.f12.codenavigator.navigation.fixtures.ClassWithDelegation
import no.f12.codenavigator.navigation.fixtures.ClassWithoutDelegation
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class BridgeMethodDetectorTest {

    private val testClassesDir: File
        get() {
            val location = ClassWithDelegation::class.java.protectionDomain.codeSource.location
            return File(location.toURI())
        }

    @Test
    fun `detects bridge methods in class with Map delegation`() {
        val bridgeMethods = BridgeMethodDetector.scanAll(listOf(testClassesDir))

        val className = ClassName("no.f12.codenavigator.navigation.fixtures.ClassWithDelegation")
        val methods = bridgeMethods.filter { it.className == className }.map { it.methodName }.toSet()
        assertTrue("entrySet" in methods, "entrySet() should be detected as bridge method, found: $methods")
        assertTrue("keySet" in methods, "keySet() should be detected as bridge method, found: $methods")
        assertTrue("size" in methods, "size() should be detected as bridge method, found: $methods")
    }

    @Test
    fun `does not flag non-bridge methods`() {
        val bridgeMethods = BridgeMethodDetector.scanAll(listOf(testClassesDir))

        val className = ClassName("no.f12.codenavigator.navigation.fixtures.ClassWithDelegation")
        val methods = bridgeMethods.filter { it.className == className }.map { it.methodName }
        assertTrue("explicitMethod" !in methods, "explicitMethod should not be detected as bridge")
        assertTrue("isEmpty" !in methods, "isEmpty() is not a bridge method")
    }

    @Test
    fun `class without bridge methods returns no results`() {
        val bridgeMethods = BridgeMethodDetector.scanAll(listOf(testClassesDir))

        val className = ClassName("no.f12.codenavigator.navigation.fixtures.ClassWithoutDelegation")
        val methods = bridgeMethods.filter { it.className == className }
        assertTrue(methods.isEmpty(), "ClassWithoutDelegation should have no bridge methods: $methods")
    }

    @Test
    fun `returns empty set for empty directory list`() {
        val bridgeMethods = BridgeMethodDetector.scanAll(emptyList())

        assertTrue(bridgeMethods.isEmpty())
    }
}
