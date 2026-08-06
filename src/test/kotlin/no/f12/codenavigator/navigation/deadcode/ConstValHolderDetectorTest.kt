package no.f12.codenavigator.navigation.deadcode

import no.f12.codenavigator.navigation.*

import no.f12.codenavigator.navigation.types.ClassName
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
    fun `returns empty set for empty directory list`() {
        val holders = ConstValHolderDetector.scanAll(emptyList())

        assertTrue(holders.isEmpty())
    }
}
