@file:Suppress("unused")

package no.f12.codenavigator.navigation.fixtures

/**
 * Test fixtures for [no.f12.codenavigator.navigation.deadcode.DelegationMethodDetector].
 * These classes are compiled by the Kotlin compiler, so they carry real
 * `@kotlin.Metadata` annotations and compiler-generated delegation methods.
 */

/** Delegates [Map] to [rawValues] — compiler generates Map methods (clear, put, etc.). */
class ClassWithDelegation(
    private val rawValues: Map<String, String>,
) : Map<String, String> by rawValues {
    fun explicitMethod(): String = "explicit"
}

/** No delegation — all methods are explicitly declared. */
class ClassWithoutDelegation {
    fun regularMethod(): String = "regular"
    fun anotherMethod(): Int = 42
}
