package com.example.variants.movefile.users

// Has a PROPERTY called `explain`; it does not use the extension function of the same name in Texts.kt.
class SourceInfo(val explain: String) {
    fun describe(): String = "source: $explain"
}
