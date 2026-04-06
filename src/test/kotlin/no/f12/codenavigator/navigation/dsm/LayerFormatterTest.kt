package no.f12.codenavigator.navigation.dsm

import no.f12.codenavigator.navigation.core.ClassName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LayerFormatterTest {

    @Test
    fun `format single outward violation`() {
        val result = LayerCheckResult(
            violations = listOf(
                LayerViolation(
                    sourceClass = ClassName("com.example.domain.Order"),
                    targetClass = ClassName("com.example.services.OrderService"),
                    sourceLayer = "domain",
                    targetLayer = "service",
                    type = ViolationType.OUTWARD,
                ),
            ),
            unassignedClasses = emptySet(),
        )

        val output = LayerFormatter.format(result)

        assertTrue(output.contains("VIOLATION: com.example.domain.Order → com.example.services.OrderService"))
        assertTrue(output.contains("domain must not depend on outer layers"))
    }

    @Test
    fun `format single peer violation`() {
        val result = LayerCheckResult(
            violations = listOf(
                LayerViolation(
                    sourceClass = ClassName("com.example.services.OrderService"),
                    targetClass = ClassName("com.example.services.CustomerService"),
                    sourceLayer = "service",
                    targetLayer = "service",
                    type = ViolationType.PEER,
                ),
            ),
            unassignedClasses = emptySet(),
        )

        val output = LayerFormatter.format(result)

        assertTrue(output.contains("VIOLATION: com.example.services.OrderService → com.example.services.CustomerService"))
        assertTrue(output.contains("exceeds peer dependency limit for service"))
    }

    @Test
    fun `format multiple violations`() {
        val result = LayerCheckResult(
            violations = listOf(
                LayerViolation(
                    sourceClass = ClassName("com.example.domain.Order"),
                    targetClass = ClassName("com.example.services.OrderService"),
                    sourceLayer = "domain",
                    targetLayer = "service",
                    type = ViolationType.OUTWARD,
                ),
                LayerViolation(
                    sourceClass = ClassName("com.example.services.OrderService"),
                    targetClass = ClassName("com.example.services.CustomerService"),
                    sourceLayer = "service",
                    targetLayer = "service",
                    type = ViolationType.PEER,
                ),
            ),
            unassignedClasses = emptySet(),
        )

        val output = LayerFormatter.format(result)

        assertTrue(output.contains("2 layer violation(s) found:"))
        assertTrue(output.contains("VIOLATION: com.example.domain.Order"))
        assertTrue(output.contains("VIOLATION: com.example.services.OrderService"))
    }

    @Test
    fun `format result with unassigned classes warning`() {
        val result = LayerCheckResult(
            violations = emptyList(),
            unassignedClasses = setOf(ClassName("com.example.util.Helper"), ClassName("com.example.common.Utils")),
        )

        val output = LayerFormatter.format(result)

        assertTrue(output.contains("No layer violations found."))
        assertTrue(output.contains("WARNING: 2 class(es) not matched by any layer pattern:"))
        assertTrue(output.contains("com.example.common.Utils"))
        assertTrue(output.contains("com.example.util.Helper"))
    }

    @Test
    fun `format result with no violations shows success message`() {
        val result = LayerCheckResult(
            violations = emptyList(),
            unassignedClasses = emptySet(),
        )

        val output = LayerFormatter.format(result)

        assertEquals("No layer violations found.", output)
    }

    @Test
    fun `format init output with sample classes`() {
        val sampleClasses = listOf(
            ClassName("com.example.api.OrderController"),
            ClassName("com.example.services.OrderService"),
            ClassName("com.example.domain.Order"),
        )

        val output = LayerFormatter.formatInit(".cnav-layers.json", 3, sampleClasses)

        assertTrue(output.contains("Generated .cnav-layers.json"))
        assertTrue(output.contains("3 classes found"))
        assertTrue(output.contains("OrderController"))
        assertTrue(output.contains("Next steps:"))
        assertTrue(output.contains("peerLimit"))
    }
}
