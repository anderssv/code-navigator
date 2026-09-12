package com.example.variants.ambient

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/** Reads the wall clock directly — the zero-arg form. */
class ExpiryPolicy {
    fun isExpired(deadline: Instant): Boolean = deadline.isBefore(Instant.now())
}

/**
 * Reads the wall clock through a zone overload. Still a violation: a zone changes *which* wall clock
 * is read, not whether one is read. This is the shape seen in the field (`ZonedDateTime.now(APP_ZONE)`).
 */
class ZonedExpiryPolicy {
    fun today(): LocalDate = LocalDate.now(ZoneId.of("Europe/Oslo"))
}

/** The corrected form: the clock is injected, so `now(clock)` is not a violation. */
class InjectedClockPolicy(private val clock: Clock) {
    fun today(): LocalDate = LocalDate.now(clock)
}

/** Nondeterministic id generation inside the domain. */
class IdFactory {
    fun newId(): String = UUID.randomUUID().toString()
}

/** Environment read inside the domain. */
class RegionLookup {
    fun region(): String? = System.getenv("REGION")
}

/** Clock read in a default constructor argument — lands in `<init>`, reported separately. */
data class StampedEvent(val name: String, val at: Instant = Instant.now())
