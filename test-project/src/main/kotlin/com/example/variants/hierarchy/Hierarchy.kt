package com.example.variants.hierarchy

interface Cacheable {
    fun cacheKey(): String
}

interface Validatable {
    fun validate(): Boolean
}

abstract class BaseService(val serviceName: String) {
    abstract fun execute(): String
}

class ConcreteService : BaseService("concrete"), Cacheable, Validatable {
    override fun execute(): String = "executed"
    override fun cacheKey(): String = "service:concrete"
    override fun validate(): Boolean = true
}

class AnotherService : BaseService("another") {
    override fun execute(): String = "another"
}

interface Fetchable : Cacheable {
    fun fetch(): String
}

class FetchService : BaseService("fetch"), Fetchable {
    override fun execute(): String = "fetch"
    override fun cacheKey(): String = "service:fetch"
    override fun fetch(): String = "data"
}
