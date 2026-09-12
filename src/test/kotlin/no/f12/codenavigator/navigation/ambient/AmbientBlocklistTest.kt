package no.f12.codenavigator.navigation.ambient

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class AmbientBlocklistTest {

    @Test
    fun `zero-arg now is a violation`() {
        assertNotNull(AmbientBlocklist.violatedBy("java/time/Instant", "now", "()Ljava/time/Instant;"))
    }

    @Test
    fun `now with an injected Clock is exempt`() {
        assertNull(AmbientBlocklist.violatedBy("java/time/LocalDate", "now", "(Ljava/time/Clock;)Ljava/time/LocalDate;"))
    }

    @Test
    fun `now with a zone is still a violation`() {
        assertNotNull(AmbientBlocklist.violatedBy("java/time/ZonedDateTime", "now", "(Ljava/time/ZoneId;)Ljava/time/ZonedDateTime;"))
    }

    @Test
    fun `only the no-arg Date constructor reads the clock`() {
        assertNotNull(AmbientBlocklist.violatedBy("java/util/Date", "<init>", "()V"))
        assertNull(AmbientBlocklist.violatedBy("java/util/Date", "<init>", "(J)V"))
    }

    @Test
    fun `wildcard members match any method on an ambient type`() {
        val signal = AmbientBlocklist.violatedBy("java/nio/file/Files", "readAllBytes", "(Ljava/nio/file/Path;)[B")
        assertEquals(AmbientCategory.IO, signal?.category)
    }

    @Test
    fun `unrelated calls are not flagged`() {
        assertNull(AmbientBlocklist.violatedBy("java/time/Clock", "instant", "()Ljava/time/Instant;"))
        assertNull(AmbientBlocklist.violatedBy("com/example/UserService", "register", "()V"))
    }

    @Test
    fun `parses categories case-insensitively and defaults to all`() {
        assertEquals(setOf(AmbientCategory.CLOCK, AmbientCategory.RANDOM), AmbientCategory.parse(listOf("clock", "RANDOM")))
        assertEquals(AmbientCategory.entries.toSet(), AmbientCategory.parse(emptyList()))
    }
}
