package no.f12.codenavigator.navigation.dsm

import no.f12.codenavigator.navigation.types.ClassName
import no.f12.codenavigator.navigation.types.PackageName
import kotlin.test.Test
import kotlin.test.assertEquals

class ModulePackageLabelsTest {

    @Test
    fun `groups modules by the same displayed package truncation used by DSM`() {
        val service = ClassName("com.example.service.OrderService")
        val shared = ClassName("com.example.shared.OrderId")

        val labels = ModulePackageLabels.build(
            projectClasses = setOf(service, shared),
            modulesOfClass = mapOf(service to setOf(":service"), shared to setOf(":shared")),
            displayPrefix = PackageName("com.example"),
            depth = 1,
        )

        assertEquals(setOf(":service"), labels[PackageName("service")])
        assertEquals(setOf(":shared"), labels[PackageName("shared")])
    }

    @Test
    fun `returns empty labels when no module provenance is supplied`() {
        val labels = ModulePackageLabels.build(
            projectClasses = setOf(ClassName("com.example.Foo")),
            modulesOfClass = emptyMap(),
            displayPrefix = PackageName("com.example"),
            depth = 1,
        )

        assertEquals(emptyMap(), labels)
    }
}
