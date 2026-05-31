package com.example.variants.safedelete

class UsedService {
    fun activeMethod(): String = "active"
    fun unusedMethod(): String = "unused"
}

class UnusedService {
    fun doSomething(): String = "something"
}

class ConsumerService {
    private val service = UsedService()

    fun consume(): String = service.activeMethod()
}
