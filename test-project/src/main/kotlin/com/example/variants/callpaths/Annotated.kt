// Entry points found by annotation; one is guarded by an annotation instead of a call.
package com.example.variants.callpaths.annotated

import com.example.variants.callpaths.Post
import com.example.variants.callpaths.RequiresRole

class Repo {
    fun delete(id: Long) {}
}

class AdminController(private val repo: Repo) {
    @Post
    @RequiresRole("admin")
    fun delete(id: Long) = repo.delete(id)
}

class PublicController(private val repo: Repo) {
    @Post
    fun delete(id: Long) = repo.delete(id)

    // Not an entry point: no @Post.
    fun internalDelete(id: Long) = repo.delete(id)
}
