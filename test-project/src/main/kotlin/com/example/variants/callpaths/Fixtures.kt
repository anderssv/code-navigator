// Fixtures for call-paths: each subpackage below is one scenario, so a test can target just its classes.
package com.example.variants.callpaths

class Auth {
    fun authorize(user: String) {}
}

annotation class Post
annotation class RequiresRole(val role: String)
