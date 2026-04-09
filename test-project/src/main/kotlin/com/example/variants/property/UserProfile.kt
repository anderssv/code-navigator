package com.example.variants.property

data class UserProfile(val fullName: String, val email: String)

class ProfileService {
    fun createProfile(name: String, email: String): UserProfile =
        UserProfile(fullName = name, email = email)

    fun displayName(profile: UserProfile): String =
        profile.fullName

    fun updateName(profile: UserProfile, newName: String): UserProfile =
        profile.copy(fullName = newName)
}
