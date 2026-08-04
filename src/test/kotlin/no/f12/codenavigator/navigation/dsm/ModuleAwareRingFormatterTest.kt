package no.f12.codenavigator.navigation.dsm

import no.f12.codenavigator.config.OutputFormat
import no.f12.codenavigator.navigation.types.ClassName
import no.f12.codenavigator.navigation.types.PackageName
import kotlin.test.Test
import kotlin.test.assertTrue

class ModuleAwareRingFormatterTest {

    @Test
    fun `package ring text labels packages with modules`() {
        val assignment = RingAssignment(
            rings = mapOf(PackageName("domain") to 0, PackageName("adapter") to 1),
            compositionRoots = emptySet(),
            violations = emptyList(),
        )

        val output = RingFormatter.format(
            assignment,
            format = OutputFormat.TEXT,
            moduleLabels = mapOf(
                PackageName("domain") to setOf(":core"),
                PackageName("adapter") to setOf(":web"),
            ),
        )

        assertTrue(output.contains("[:core] domain"), output)
        assertTrue(output.contains("[:web] adapter"), output)
    }

    @Test
    fun `emergent ring text labels classes with modules`() {
        val domain = ClassName("com.example.domain.Order")
        val adapter = ClassName("com.example.web.OrderRoute")
        val assignment = ClassRingAssignment(
            classRings = mapOf(domain to 0, adapter to 1),
            packageSummary = emptyMap(),
            violations = emptyList(),
        )

        val output = EmergentRingFormatter.format(
            assignment,
            modulesOfClass = mapOf(domain to setOf(":core"), adapter to setOf(":web")),
        )

        assertTrue(output.contains("[:core] com.example.domain.Order"), output)
        assertTrue(output.contains("[:web] com.example.web.OrderRoute"), output)
    }

    @Test
    fun `emergent ring JSON adds class module fields`() {
        val domain = ClassName("com.example.domain.Order")
        val assignment = ClassRingAssignment(
            classRings = mapOf(domain to 0),
            packageSummary = emptyMap(),
            violations = emptyList(),
        )

        val output = EmergentRingFormatter.formatJson(assignment, modulesOfClass = mapOf(domain to setOf(":core")))

        assertTrue(output.contains("\"modules\":[\":core\"]"), output)
    }
}
