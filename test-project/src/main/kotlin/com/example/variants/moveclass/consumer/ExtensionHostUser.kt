package com.example.variants.moveclass.consumer

import com.example.variants.moveclass.original.ExtensionHost

// This file imports ExtensionHost (which does not move) but NOT the extension functions.
// After moving ExtensionFunctions.kt, this import must NOT be rewritten.
class ExtensionHostUser {
    fun use(): String = ExtensionHost("hello").value
}
