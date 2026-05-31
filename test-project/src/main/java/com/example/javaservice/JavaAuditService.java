package com.example.javaservice;

public class JavaAuditService {

    public String formatAuditEntry(String name, String email) {
        return "audit: " + name + " <" + email + ">";
    }

    public String auditUser(String name, String email) {
        return formatAuditEntry(name, email);
    }
}
