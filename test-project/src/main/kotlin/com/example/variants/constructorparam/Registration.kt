package com.example.variants.constructorparam

data class Registration(val fullName: String, val email: String)

class RegistrationService {
    fun register(fullName: String, email: String): Registration =
        Registration(fullName = fullName, email = email)
}
