package com.example.variants.overridefamily

interface RaClient {
    fun getInfo(id: String): String
}

class RaClientImpl : RaClient {
    override fun getInfo(id: String): String = "impl:$id"
}

class RaClientFake : RaClient {
    override fun getInfo(id: String): String = "fake:$id"
}
