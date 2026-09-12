@file:Suppress("NOTHING_TO_INLINE", "unused")

package no.f12.codenavigator.navigation.fixtures

/**
 * Test fixtures for [no.f12.codenavigator.navigation.bytecode.InlineMethodDetector].
 * These classes are compiled by the Kotlin compiler, so they carry real
 * `@kotlin.Metadata` annotations that the detector can parse.
 */
class ClassWithInline {
    inline fun inlineMethod(block: () -> Unit) = block()
    fun normalMethod() {}
}

class ClassWithoutInline {
    fun regularMethod() {}
    fun anotherMethod() {}
}

class ClassWithMixedMethods {
    inline fun <reified T> inlineReified(): String = T::class.simpleName ?: ""
    fun normalHelper(): String = "hello"
    inline fun inlineSimple(block: () -> Int): Int = block()
}
