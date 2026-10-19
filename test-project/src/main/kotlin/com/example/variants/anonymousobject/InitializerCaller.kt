package com.example.variants.anonymousobject

object InitializerTarget {
    fun viaProperty(): String = "p"
    fun viaInitBlock(): String = "i"
    fun viaObject(): String = "o"
}

class InitializerCaller {
    val property: String = InitializerTarget.viaProperty()

    init {
        InitializerTarget.viaInitBlock()
    }
}

object InitializerObject {
    val value: String = InitializerTarget.viaObject()
}
