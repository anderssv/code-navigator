package com.example.variants.moveclass.consumer

import com.example.variants.moveclass.original.Notifier

class NotificationService(private val notifier: Notifier) {
    fun notifyUser(message: String) {
        notifier.send(message)
    }
}
