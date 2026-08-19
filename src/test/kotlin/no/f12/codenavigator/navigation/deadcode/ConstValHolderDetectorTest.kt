package no.f12.codenavigator.navigation.deadcode

import no.f12.codenavigator.navigation.*

import no.f12.codenavigator.navigation.types.ClassName
import no.f12.codenavigator.navigation.fixtures.ClassWithCompanionConstVals
import no.f12.codenavigator.navigation.fixtures.ClassWithCompanionConstValsAndFunction
import no.f12.codenavigator.navigation.fixtures.ClassWithCompanionNoConstVals
import no.f12.codenavigator.navigation.fixtures.MixedConstValAndFunction
import no.f12.codenavigator.navigation.fixtures.NoConstValsHere
import no.f12.codenavigator.navigation.fixtures.PureConstValHolder
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class ConstValHolderDetectorTest {

    private val testClassesDir: File
        get() {
            val location = PureConstValHolder::class.java.protectionDomain.codeSource.location
            return File(location.toURI())
        }

    @Test
    fun `detects object with only const val members as a const val holder`() {
        val holders = ConstValHolderDetector.scanAll(listOf(testClassesDir))

        val pureHolder = ClassName("no.f12.codenavigator.navigation.fixtures.PureConstValHolder")
        assertTrue(pureHolder in holders, "PureConstValHolder should be detected as a const val holder")
    }

    @Test
    fun `detects nested object with only const val members as a const val holder`() {
        val holders = ConstValHolderDetector.scanAll(listOf(testClassesDir))

        val nestedHolder = ClassName("no.f12.codenavigator.navigation.fixtures.PureConstValHolder\$NestedConstValHolder")
        assertTrue(nestedHolder in holders, "Nested const val holder should be detected")
    }

    @Test
    fun `does not flag object with functions as a const val holder`() {
        val holders = ConstValHolderDetector.scanAll(listOf(testClassesDir))

        val mixed = ClassName("no.f12.codenavigator.navigation.fixtures.MixedConstValAndFunction")
        assertTrue(mixed !in holders, "MixedConstValAndFunction has functions and should not be treated as a pure const val holder")
    }

    @Test
    fun `does not flag object with no const vals as a const val holder`() {
        val holders = ConstValHolderDetector.scanAll(listOf(testClassesDir))

        val noConsts = ClassName("no.f12.codenavigator.navigation.fixtures.NoConstValsHere")
        assertTrue(noConsts !in holders, "NoConstValsHere has no const vals and should not be flagged")
    }

    @Test
    fun `detects class with companion object holding only const vals as a const val holder`() {
        val holders = ConstValHolderDetector.scanAll(listOf(testClassesDir))

        val outerClass = ClassName("no.f12.codenavigator.navigation.fixtures.ClassWithCompanionConstVals")
        assertTrue(outerClass in holders, "ClassWithCompanionConstVals should be detected via its companion's const vals")
    }

    @Test
    fun `detects companion class itself as a const val holder`() {
        val holders = ConstValHolderDetector.scanAll(listOf(testClassesDir))

        val companion = ClassName("no.f12.codenavigator.navigation.fixtures.ClassWithCompanionConstVals\$Companion")
        assertTrue(companion in holders, "Companion class itself should be detected as a const val holder")
    }

    @Test
    fun `detects outer class even when it has functions alongside companion const vals`() {
        val holders = ConstValHolderDetector.scanAll(listOf(testClassesDir))

        val outerClass = ClassName("no.f12.codenavigator.navigation.fixtures.ClassWithCompanionConstValsAndFunction")
        assertTrue(outerClass in holders, "Outer class should be detected via its companion's const vals — its functions don't change that const val references are inlined")
    }

    @Test
    fun `does not flag class with companion holding non-const properties`() {
        val holders = ConstValHolderDetector.scanAll(listOf(testClassesDir))

        val outerClass = ClassName("no.f12.codenavigator.navigation.fixtures.ClassWithCompanionNoConstVals")
        assertTrue(outerClass !in holders, "ClassWithCompanionNoConstVals has non-const properties and should not be flagged")
    }

    @Test
    fun `returns empty set for empty directory list`() {
        val holders = ConstValHolderDetector.scanAll(emptyList())

        assertTrue(holders.isEmpty())
    }
}
