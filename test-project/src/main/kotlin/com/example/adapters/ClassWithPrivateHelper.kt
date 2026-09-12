package com.example.adapters

/**
 * A private helper's parameter/return type is never visible to callers of this class — it's an
 * implementation detail of how the class does its work internally, not part of its public contract.
 * `SignatureTypeScanner` should treat it the same as a type touched only inside a method body.
 */
class ClassWithPrivateHelper {
    fun publicMethod(): String = createInternal().toString()

    private fun createInternal(): java.net.URL = java.net.URL("https://example.com")
}
