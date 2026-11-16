package com.example.variants.position

class PersonUser {
    fun use(): Int = Person("a").actionsOn(1).size + Person("b").greet().length
}
