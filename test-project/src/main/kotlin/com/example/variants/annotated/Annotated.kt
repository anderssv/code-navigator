package com.example.variants.annotated

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class Service(val name: String = "")

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Scheduled(val cron: String = "")

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Transactional

@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class Inject

@Service("payment")
class PaymentProcessor {
    @Inject
    val gateway: String = "stripe"

    @Scheduled(cron = "0 * * * *")
    fun processPayments() {
        println("processing")
    }

    @Transactional
    fun refund(orderId: String): Boolean = true

    fun status(): String = "ok"
}

@Service("notifications")
class NotificationProcessor {
    @Scheduled(cron = "*/5 * * * *")
    fun sendPending() {
        println("sending")
    }
}

class PlainService {
    fun doWork() {}
}
