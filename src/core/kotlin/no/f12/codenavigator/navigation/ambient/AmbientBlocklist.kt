package no.f12.codenavigator.navigation.ambient

/**
 * The kind of ambient (nondeterministic, environment-dependent) input a domain class reached for.
 * Separate categories rather than one "impurity" bucket because the fixes differ: a clock read is
 * fixed by injecting a `Clock`, a random read by injecting an id/number generator, an environment
 * read by passing configuration in, and I/O by moving the call behind a port.
 */
enum class AmbientCategory {
    CLOCK,
    RANDOM,
    ENV,
    IO,
    ;

    companion object {
        fun parse(values: List<String>): Set<AmbientCategory> {
            if (values.isEmpty()) return entries.toSet()
            return values.map { value ->
                entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
                    ?: error("Invalid category '$value'. Must be one of: ${entries.joinToString(",") { it.name.lowercase() }}.")
            }.toSet()
        }
    }
}

/**
 * One blocklisted call: an ambient read that a domain class has no business performing directly.
 *
 * [exemptDescriptorMarker] is what makes this check safe to run against a codebase that already
 * does the right thing. `LocalDate.now()` and `LocalDate.now(clock)` differ *only* in the method
 * descriptor — same owner, same name — so a check keyed on (owner, name) alone would flag the
 * injected-clock form, i.e. precisely the projects that already fixed this. Keying the exemption on
 * the descriptor containing `Ljava/time/Clock;` also (correctly) leaves `now(ZoneId)` flagged: a
 * zone changes which wall clock is read, not whether one is read.
 *
 * [requiredDescriptor] is the opposite narrowing, for calls that are only ambient in their
 * no-argument form — `Date()` reads the clock, `Date(millis)` is a value constructed from a
 * timestamp the caller already had.
 *
 * [member] may be `*`, meaning every member of the owner (used where the whole type is ambient,
 * e.g. `java.nio.file.Files`).
 */
data class AmbientSignal(
    val owner: String,
    val member: String,
    val category: AmbientCategory,
    val label: String,
    val exemptDescriptorMarker: String? = null,
    val requiredDescriptor: String? = null,
) {
    fun matches(owner: String, member: String): Boolean =
        this.owner == owner && (this.member == "*" || this.member == member)

    fun isViolation(descriptor: String): Boolean {
        if (requiredDescriptor != null && descriptor != requiredDescriptor) return false
        if (exemptDescriptorMarker != null && descriptor.contains(exemptDescriptorMarker)) return false
        return true
    }
}

object AmbientBlocklist {

    private const val CLOCK_PARAM = "Ljava/time/Clock;"

    private val JAVA_TIME_NOW_TYPES = listOf(
        "Instant", "LocalDate", "LocalDateTime", "LocalTime",
        "ZonedDateTime", "OffsetDateTime", "OffsetTime",
        "Year", "YearMonth", "MonthDay",
    )

    val SIGNALS: List<AmbientSignal> =
        JAVA_TIME_NOW_TYPES.map { type ->
            AmbientSignal(
                owner = "java/time/$type",
                member = "now",
                category = AmbientCategory.CLOCK,
                label = "$type.now()",
                exemptDescriptorMarker = CLOCK_PARAM,
            )
        } + listOf(
            AmbientSignal("java/lang/System", "currentTimeMillis", AmbientCategory.CLOCK, "System.currentTimeMillis()"),
            AmbientSignal("java/lang/System", "nanoTime", AmbientCategory.CLOCK, "System.nanoTime()"),
            AmbientSignal("java/util/Date", "<init>", AmbientCategory.CLOCK, "Date()", requiredDescriptor = "()V"),
            AmbientSignal("java/util/Calendar", "getInstance", AmbientCategory.CLOCK, "Calendar.getInstance()"),
            AmbientSignal("java/time/Clock", "systemUTC", AmbientCategory.CLOCK, "Clock.systemUTC()"),
            AmbientSignal("java/time/Clock", "systemDefaultZone", AmbientCategory.CLOCK, "Clock.systemDefaultZone()"),
            AmbientSignal("java/time/Clock", "system", AmbientCategory.CLOCK, "Clock.system()"),
            AmbientSignal("kotlinx/datetime/Clock\$System", "now", AmbientCategory.CLOCK, "Clock.System.now()"),
            AmbientSignal("kotlin/time/Clock\$System", "now", AmbientCategory.CLOCK, "Clock.System.now()"),

            AmbientSignal("java/util/UUID", "randomUUID", AmbientCategory.RANDOM, "UUID.randomUUID()"),
            AmbientSignal("java/lang/Math", "random", AmbientCategory.RANDOM, "Math.random()"),
            AmbientSignal("java/util/Random", "<init>", AmbientCategory.RANDOM, "Random()", requiredDescriptor = "()V"),
            AmbientSignal("java/security/SecureRandom", "<init>", AmbientCategory.RANDOM, "SecureRandom()", requiredDescriptor = "()V"),
            AmbientSignal("kotlin/random/Random\$Default", "*", AmbientCategory.RANDOM, "Random.Default"),

            AmbientSignal("java/lang/System", "getenv", AmbientCategory.ENV, "System.getenv()"),
            AmbientSignal("java/lang/System", "getProperty", AmbientCategory.ENV, "System.getProperty()"),
            AmbientSignal("java/lang/System", "getProperties", AmbientCategory.ENV, "System.getProperties()"),

            AmbientSignal("java/nio/file/Files", "*", AmbientCategory.IO, "Files"),
            AmbientSignal("kotlin/io/FilesKt", "*", AmbientCategory.IO, "kotlin.io file extensions"),
            AmbientSignal("java/io/FileInputStream", "<init>", AmbientCategory.IO, "FileInputStream()"),
            AmbientSignal("java/io/FileOutputStream", "<init>", AmbientCategory.IO, "FileOutputStream()"),
            AmbientSignal("java/net/URL", "openStream", AmbientCategory.IO, "URL.openStream()"),
            AmbientSignal("java/net/URL", "openConnection", AmbientCategory.IO, "URL.openConnection()"),
            AmbientSignal("java/net/Socket", "<init>", AmbientCategory.IO, "Socket()"),
            AmbientSignal("java/lang/ProcessBuilder", "start", AmbientCategory.IO, "ProcessBuilder.start()"),
            AmbientSignal("java/lang/Runtime", "exec", AmbientCategory.IO, "Runtime.exec()"),
        )

    private val byOwner: Map<String, List<AmbientSignal>> = SIGNALS.groupBy { it.owner }

    /** The signal violated by a call, or null when the call is either not blocklisted or exempt. */
    fun violatedBy(owner: String, member: String, descriptor: String): AmbientSignal? =
        byOwner[owner]
            ?.firstOrNull { it.matches(owner, member) }
            ?.takeIf { it.isViolation(descriptor) }
}
