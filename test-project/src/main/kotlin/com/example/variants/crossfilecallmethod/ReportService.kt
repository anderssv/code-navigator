package com.example.variants.crossfilecallmethod

class ReportService(
    private val auditService: AuditService,
) {
    fun generateReport(userId: String): String =
        auditService.formatAuditEntry("test", "test@example.com")
}
