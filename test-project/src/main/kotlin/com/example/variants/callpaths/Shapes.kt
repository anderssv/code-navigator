// Graph shapes: a cycle and a lambda body. Fan-out and a long chain are in Chains.kt.
package com.example.variants.callpaths.shapes

class Repo {
    fun delete(id: Long) {}
}

class Ping(private val repo: Repo) {
    lateinit var pong: Pong

    fun ping(id: Long, n: Int) {
        if (n > 0) pong.pong(id, n - 1) else repo.delete(id)
    }
}

class Pong(private val ping: Ping) {
    fun pong(id: Long, n: Int) = ping.ping(id, n)
}

class Scheduler(private val repo: Repo) {
    fun schedule(id: Long) = later { repo.delete(id) }

    private fun later(block: () -> Unit) = block()
}
