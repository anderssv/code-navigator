package com.example.variants.position

class Person(val name: String) {

    fun actionsOn(day: Int): List<String> {
        val prefix = name.uppercase()
        return listOf("$prefix:$day")
    }

    fun greet() = "hi $name"
}
