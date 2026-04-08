package com.example.variants.moveclass.consumer

import com.example.variants.moveclass.original.PaymentService

class ReportService {
    fun generateReport(service: PaymentService): String {
        return "Report for payment service"
    }
}
