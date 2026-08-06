@file:Suppress("unused")

package no.f12.codenavigator.navigation.fixtures

/**
 * Test fixtures for [no.f12.codenavigator.navigation.deadcode.ConstValHolderDetector].
 * These classes are compiled by the Kotlin compiler, so they carry real
 * `@kotlin.Metadata` annotations that the detector can parse.
 *
 * Kotlin inlines `const val` references as literal values at every call site
 * (no `GETSTATIC`-style bytecode edge survives back to the declaring class),
 * so a purely bytecode-based dead-code analyzer has nothing to find even when
 * a const-val holder is used dozens of times across the source tree.
 */
object PureConstValHolder {
    const val FIRST = "first"
    const val SECOND = "second"

    object NestedConstValHolder {
        const val NESTED = "nested"
    }
}

object MixedConstValAndFunction {
    const val FIRST = "first"

    fun doSomething(): String = "hello"
}

object NoConstValsHere {
    fun doSomething(): String = "hello"
    fun doSomethingElse(): String = "world"
}
