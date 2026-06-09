package com.example.variants.moveclass.original

// Extension functions on ExtensionHost — this file will be moved, ExtensionHost stays.
fun ExtensionHost.doubled(): String = value + value

fun ExtensionHost.withPrefix(prefix: String): ExtensionHost = ExtensionHost("$prefix${value}")

fun plain(): String = "plain"
