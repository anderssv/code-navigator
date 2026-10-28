package com.example.variants.movefile.web

class NotFoundException(message: String) : RuntimeException(message)

fun dataSourceRoutes(): List<String> = listOf("a", "b")

const val DEFAULT_ROUTE = "/"
