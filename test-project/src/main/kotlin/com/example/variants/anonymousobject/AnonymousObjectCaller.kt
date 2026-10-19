package com.example.variants.anonymousobject

object Registry {
    fun register(name: String): String = "registered $name"
}

interface Handler {
    val label: String
}

// The only call to Registry.register happens in the anonymous object's property initializer, which
// the compiler emits into AnonymousObjectContext$build$1.<init> — a constructor, not a named method.
class AnonymousObjectContext {
    fun build(): Handler = object : Handler {
        override val label: String = Registry.register("from-initializer")
    }
}
