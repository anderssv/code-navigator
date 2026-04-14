package com.example.infra

class EventSender {
    companion object {
        fun load(config: String = "default"): EventSender {
            return EventSender()
        }
    }

    fun send(event: String) {
        println("Sending: $event")
    }
}
