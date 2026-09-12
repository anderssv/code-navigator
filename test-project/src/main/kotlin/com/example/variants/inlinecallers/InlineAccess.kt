package com.example.variants.inlinecallers

class PollContext(val id: String)

/** Inlined into every call site, so no call edge survives for bytecode analysis to find. */
inline fun withPoll(id: String, block: (PollContext) -> String): String = block(PollContext(id))

fun notInlined(id: String): String = id

class AdminRoutes {
    fun handleEdit(): String = withPoll("edit") { it.id }
    fun handleDelete(): String = withPoll("delete") { it.id }
    fun handleList(): String = notInlined("list")
}
