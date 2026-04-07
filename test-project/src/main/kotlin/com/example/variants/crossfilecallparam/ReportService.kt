package com.example.variants.crossfilecallparam

class ReportService(
    private val auditService: AuditService,
) {
    fun generateReport(userName: String, userEmail: String): String =
        auditService.formatAuditEntry(name = userName, email = userEmail)
}
