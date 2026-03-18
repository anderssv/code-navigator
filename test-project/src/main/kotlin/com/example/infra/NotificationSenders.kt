package com.example.infra

import com.example.domain.NotificationSender

class EmailNotificationSender : NotificationSender {
    override fun send(userId: String, message: String) {
        println("Sending email to $userId: $message")
    }
}

class NoOpNotificationSender : NotificationSender {
    override fun send(userId: String, message: String) {
        // intentionally empty
    }
}
