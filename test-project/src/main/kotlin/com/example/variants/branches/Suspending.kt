// Fixture for --branches on coroutines: a suspend function, a suspend lambda, a suspend caller.
package com.example.variants.branches

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay

class Notifier { suspend fun send(id: Long) { delay(1) } }

class AsyncOrders(private val repo: Repo, private val notifier: Notifier) {
    suspend fun remove(id: Long, hard: Boolean) {
        delay(1)
        if (hard) {
            repo.delete(id)
        } else {
            repo.archive(id)
        }
        notifier.send(id)
    }

    suspend fun removeAll(ids: List<Long>) = coroutineScope {
        for (id in ids) if (id > 0) repo.delete(id)
    }
}

class AsyncController(private val orders: AsyncOrders) {
    suspend fun remove(id: Long) {
        try { orders.remove(id, hard = false) } catch (e: IllegalStateException) { }
    }
}
