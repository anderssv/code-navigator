package com.example.javaservice;

public class JavaReportService {

    private final JavaAuditService auditService;

    public JavaReportService(JavaAuditService auditService) {
        this.auditService = auditService;
    }

    public String generateReport(String name, String email) {
        return "Report: " + auditService.formatAuditEntry(name, email);
    }
}
