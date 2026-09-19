package no.f12.codenavigator.navigation.dsm

import no.f12.codenavigator.navigation.bytecode.scanProjectClasses
import no.f12.codenavigator.navigation.types.ClassName
import no.f12.codenavigator.navigation.types.Scope
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Runs the whole ring pipeline over real Kotlin compiler output from test-project, so the
 * inversion detection is validated against actual bytecode rather than hand-built graphs.
 */
class InversionRingsIntegrationTest {

    @TempDir
    lateinit var tempDir: Path

    private val classesDir = File("test-project/build/classes/kotlin/main")

    private fun analyse(): HexRingsOutput {
        val projectClasses = scanProjectClasses(listOf(classesDir))
        return RingsOrchestrator.analyse(
            classDirectories = listOf(classesDir),
            projectClasses = projectClasses,
            taggedDirs = emptyList(),
            scope = Scope.ALL,
            plan = emptyList(),
            config = RingsConfig(),
            reportFile = tempDir.resolve("skipped.txt").toFile(),
            modulesOfClass = emptyMap(),
        )
    }

    @Test
    fun `an adapter naming a framework type in its signature is reported as a signature adapter`() {
        val output = analyse()

        assertEquals(
            AdapterReason.FRAMEWORK_SIGNATURE,
            output.graph.adapterReasons[ClassName("com.example.adapters.JdbcUserRepository")],
        )
    }

    @Test
    fun `a class touching a framework only inside a method body is the weaker framework signal`() {
        val output = analyse()

        assertEquals(
            AdapterReason.FRAMEWORK_TYPE,
            output.graph.adapterReasons[ClassName("com.example.adapters.SqlHealthCheck")],
        )
    }

    @Test
    fun `an unreferenced class that no composition root wires is not an adapter`() {
        val output = analyse()

        assertEquals(
            null,
            output.graph.adapterReasons[ClassName("com.example.infra.EmailNotificationSender")],
        )
    }

    @Test
    fun `the port it implements is recognised as an inversion boundary`() {
        val output = analyse()

        assertEquals(RingDiagnosis.LAYERED, output.layering.diagnosis)
        assertTrue(output.layering.ringCount >= 2, "expected at least one boundary, got ${output.layering.ringCount}")
    }

    @Test
    fun `the adapter sits strictly outside the port it implements`() {
        val output = analyse()

        val adapterRing = output.layering.rings[ClassName("com.example.adapters.JdbcUserRepository")]!!
        val portRing = output.layering.rings[ClassName("com.example.domain.UserRepository")]!!

        assertTrue(adapterRing > portRing, "adapter ring $adapterRing should be outside port ring $portRing")
    }

    // Reversed deliberately (v0.1.118-SNAPSHOT, field report from greitt): this used to assert the
    // opposite. Every other adapter rule keys on evidence of touching the outside, and an in-memory
    // implementation trips none of them — so it landed in the service tier while the implementation it
    // stands in for sat in the adapter ring. Both implement the same port; a port implementation's
    // ring follows the boundary it implements, not whether it happens to open a socket. Calling it
    // "service tier" was the wrong half of the asymmetry: it is not a service either.
    @Test
    fun `an in-memory implementation is classified with the adapter it stands in for`() {
        val output = analyse()

        assertEquals(
            AdapterReason.PORT_IMPLEMENTATION,
            output.graph.adapterReasons[ClassName("com.example.infra.InMemoryUserRepository")],
        )
        assertEquals(
            ClassName("com.example.domain.UserRepository"),
            output.graph.adapterEvidence[ClassName("com.example.infra.InMemoryUserRepository")],
            "evidence should name the port it implements",
        )
    }

    @Test
    fun `an in-memory implementation sits in the same ring as the adapter it stands in for`() {
        val output = analyse()

        assertEquals(
            output.layering.rings[ClassName("com.example.adapters.JdbcUserRepository")],
            output.layering.rings[ClassName("com.example.infra.InMemoryUserRepository")],
        )
    }
}
