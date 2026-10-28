package com.example.variants.movefile.users

import com.example.variants.movefile.users.model.Actor
import com.example.variants.movefile.users.model.Reason

// Extensions on a nullable receiver: the receiver type is not what is declared here.
fun Actor?.displayName(): String = this?.name ?: "unknown"

fun Reason?.explain(): String = this?.text ?: "none"

private fun orderId(): String = "order-1"

fun greet(actor: Actor?): String = "Hello ${actor.displayName()} ${orderId()}"
