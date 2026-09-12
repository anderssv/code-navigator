package no.f12.codenavigator.navigation.ambient

import no.f12.codenavigator.navigation.types.ClassName
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AmbientCallScannerTest {

    private val testProjectClasses = File("test-project/build/classes/kotlin/main")

    private val scan = AmbientCallScanner.scan(listOf(testProjectClasses)).data

    private fun callsIn(simpleName: String): List<AmbientCall> =
        scan.calls.filter { it.callerClass.value.endsWith(".$simpleName") }

    @Test
    fun `flags a zero-arg wall clock read`() {
        val calls = callsIn("ExpiryPolicy")

        assertEquals(1, calls.size)
        assertEquals(AmbientCategory.CLOCK, calls[0].category)
        assertEquals("Instant.now()", calls[0].signal.label)
        assertEquals("isExpired", calls[0].callerMethod)
        assertEquals(AmbientSite.METHOD_BODY, calls[0].site)
    }

    // The field case: a zone overload still reads the wall clock, so keying the exemption on arity
    // instead of on the descriptor containing Clock would wrongly let this through.
    @Test
    fun `flags a wall clock read through a zone overload`() {
        val calls = callsIn("ZonedExpiryPolicy")

        assertEquals(1, calls.size)
        assertEquals(AmbientCategory.CLOCK, calls[0].category)
        assertEquals("LocalDate.now()", calls[0].signal.label)
    }

    @Test
    fun `does not flag a clock read through an injected Clock`() {
        assertEquals(emptyList(), callsIn("InjectedClockPolicy"))
    }

    @Test
    fun `records a class holding a Clock as clock-injecting`() {
        assertTrue(ClassName("com.example.variants.ambient.InjectedClockPolicy") in scan.clockInjectingClasses)
    }

    @Test
    fun `flags random id generation`() {
        val calls = callsIn("IdFactory")

        assertEquals(1, calls.size)
        assertEquals(AmbientCategory.RANDOM, calls[0].category)
        assertEquals("UUID.randomUUID()", calls[0].signal.label)
    }

    @Test
    fun `flags an environment read`() {
        val calls = callsIn("RegionLookup")

        assertEquals(1, calls.size)
        assertEquals(AmbientCategory.ENV, calls[0].category)
    }

    @Test
    fun `reports a default constructor argument as an initializer site`() {
        val calls = callsIn("StampedEvent")

        assertTrue(calls.isNotEmpty(), "expected the default-argument clock read to be found")
        assertTrue(calls.all { it.site == AmbientSite.INITIALIZER }, "expected initializer site, got ${calls.map { it.callerMethod }}")
    }

    @Test
    fun `records source file and line for a finding`() {
        val call = callsIn("ExpiryPolicy").single()

        assertEquals("AmbientReads.kt", call.sourceFile)
        assertNotNull(call.line)
        assertTrue(call.location().startsWith("AmbientReads.kt:"))
    }
}
