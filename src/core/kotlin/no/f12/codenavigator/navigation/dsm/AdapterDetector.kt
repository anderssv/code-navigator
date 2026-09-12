package no.f12.codenavigator.navigation.dsm

import no.f12.codenavigator.navigation.types.ClassName

/**
 * [evidence] is the specific external type that triggered [reason], when there is one to point at
 * (absent for [AdapterReason.CONFIGURED], which comes from a cnav-config.json override rather than
 * a class reference). Surfacing it lets a reader judge whether a classification is a genuine I/O
 * signal or a gap in cnav's built-in package lists, without re-deriving it from bytecode by hand.
 */
data class AdapterFinding(
    val reason: AdapterReason,
    val evidence: ClassName? = null,
)

object AdapterDetector {

    fun detect(
        projectClasses: Set<ClassName>,
        projectDeps: List<PackageDependency>,
        externalDeps: List<PackageDependency>,
        signatureTypes: Map<ClassName, Set<ClassName>> = emptyMap(),
        compositionRoots: Set<ClassName> = emptySet(),
        extraValuePackages: Set<String> = emptySet(),
        extraFrameworkPackages: Set<String> = emptySet(),
    ): Map<ClassName, AdapterFinding> =
        projectClasses
            .mapNotNull { cls ->
                findingFor(cls, projectClasses, projectDeps, externalDeps, signatureTypes, compositionRoots, extraValuePackages, extraFrameworkPackages)
                    ?.let { cls to it }
            }
            .toMap()

    /**
     * Framework-only pass, used before composition roots are known. The topological rules need the
     * roots, and root detection needs to know what an adapter is — this breaks that cycle by answering
     * the half of the question that depends on nothing but the class itself.
     */
    fun detectFrameworkAdapters(
        projectClasses: Set<ClassName>,
        externalDeps: List<PackageDependency>,
        signatureTypes: Map<ClassName, Set<ClassName>> = emptyMap(),
        extraFrameworkPackages: Set<String> = emptySet(),
    ): Map<ClassName, AdapterFinding> =
        projectClasses
            .mapNotNull { cls -> frameworkFindingFor(cls, projectClasses, externalDeps, signatureTypes, extraFrameworkPackages)?.let { cls to it } }
            .toMap()

    private fun frameworkFindingFor(
        cls: ClassName,
        projectClasses: Set<ClassName>,
        externalDeps: List<PackageDependency>,
        signatureTypes: Map<ClassName, Set<ClassName>>,
        extraFrameworkPackages: Set<String>,
    ): AdapterFinding? {
        // signatureTypes carries every type named in a signature position, project-internal or not
        // (that's what makes it useful elsewhere) — but a framework signal must be a real external
        // type. Without this filter, a project whose own root package happens to collide with a
        // framework prefix (e.g. org.springframework.samples.petclinic, nested under the exact
        // prefix used to detect real Spring usage) would have every class self-match "framework"
        // merely by referencing another class in the same project.
        signatureTypes[cls].orEmpty().filter { it !in projectClasses }.firstOrNull { isFrameworkType(it, extraFrameworkPackages) }?.let {
            return AdapterFinding(AdapterReason.FRAMEWORK_SIGNATURE, it)
        }
        externalDeps.firstOrNull { it.sourceClass == cls && it.targetClass !in projectClasses && isFrameworkType(it.targetClass, extraFrameworkPackages) }?.let {
            return AdapterFinding(AdapterReason.FRAMEWORK_TYPE, it.targetClass)
        }
        return null
    }

    private fun findingFor(
        cls: ClassName,
        projectClasses: Set<ClassName>,
        projectDeps: List<PackageDependency>,
        externalDeps: List<PackageDependency>,
        signatureTypes: Map<ClassName, Set<ClassName>>,
        compositionRoots: Set<ClassName>,
        extraValuePackages: Set<String>,
        extraFrameworkPackages: Set<String>,
    ): AdapterFinding? {
        frameworkFindingFor(cls, projectClasses, externalDeps, signatureTypes, extraFrameworkPackages)?.let { return it }

        // Every class references String and Intrinsics; counting those as "talks to a library" would
        // make every leaf an adapter. The topological signals only mean anything for real libraries.
        val external = externalDeps.filter { it.sourceClass == cls }.map { it.targetClass }
        val libraryEvidence = external.firstOrNull { isLibraryType(it, extraValuePackages) } ?: return null

        val callers = projectDeps
            .filter { it.targetClass == cls && it.sourceClass != cls }
            .map { it.sourceClass }
            .toSet()

        // Nothing in the application calls it, but the composition root wires it: the framework drives
        // it. Without that wiring it is simply unreferenced — dead code or a test fixture, not an
        // adapter — which is why "no callers at all" deliberately falls through to null.
        if (callers.isNotEmpty() && callers.all { it in compositionRoots }) {
            return AdapterFinding(AdapterReason.UNCALLED_ENTRY_POINT, libraryEvidence)
        }
        if (callers.isEmpty()) return null

        val outgoing = projectDeps.filter { it.sourceClass == cls && it.targetClass != cls && it.targetClass in projectClasses }
        if (outgoing.isEmpty()) return AdapterFinding(AdapterReason.SINK_WITH_EXTERNAL_CALLS, libraryEvidence)

        return null
    }

    private fun isLibraryType(type: ClassName, extraValuePackages: Set<String>): Boolean =
        (NON_ADAPTER_SIGNAL_PACKAGES + extraValuePackages).none { prefix -> type.value.startsWith(prefix) }

    private fun isFrameworkType(type: ClassName, extraFrameworkPackages: Set<String>): Boolean {
        // Value libraries and logging are a carve-out that beats even a broad framework prefix
        // (e.g. bare "javax."): javax.xml.datatype is JAXB's pure date value type, no I/O of its
        // own, but it would otherwise match "javax." — the same broad prefix that correctly covers
        // javax.net.ssl (TLS), javax.xml.parsers (XML parsing), and javax.security.auth
        // (certificates) elsewhere in real code. Narrowing "javax." itself risks silently losing
        // those real signals; excluding known non-I/O types first is safer and more precise.
        if ((VALUE_LIBRARY_PACKAGES + LOGGING_PACKAGES).any { type.value.startsWith(it) }) return false
        return (FRAMEWORK_PACKAGES + extraFrameworkPackages).any { prefix -> type.value.startsWith(prefix) }
    }

    private val STDLIB_PACKAGES = setOf(
        "java.lang.", "java.util.", "java.math.", "java.time.", "java.text.",
        "java.security.", "java.nio.charset.",
        "kotlin.", "kotlinx.coroutines.",
        "org.jetbrains.annotations.",
    )

    // Exact JDK marker interfaces (not prefixes): implementing them declares eligibility for a JDK
    // mechanism but performs no I/O of its own — java.io.Serializable is the classic case (a JPA
    // @MappedSuperclass base entity implementing it, for example, is not thereby an adapter).
    // Deliberately narrow and exact-match rather than a broad "java.io." prefix, since java.io.File/
    // java.io.InputStream etc. are real filesystem I/O and must keep counting as adapter signals.
    private val JDK_MARKER_INTERFACES = setOf(
        "java.io.Serializable",
    )

    // Pure value/DSL libraries: types that carry data or build markup, with no I/O of their own.
    // A class that only touches these (dates, HTML tag builders, serialization annotations) is not
    // an adapter just because it "talks to a library" — the SINK_WITH_EXTERNAL_CALLS signal is meant
    // to catch classes wrapping real infrastructure (a DB client, a cache client), not a leaf value
    // type or a markup helper. Framework/infra libraries stay covered separately via FRAMEWORK_PACKAGES.
    // A project can extend this list per-project via cnav-config.json's rings.valuePackages, for
    // libraries too niche or project-specific to belong in this built-in list.
    private val VALUE_LIBRARY_PACKAGES = setOf(
        "kotlinx.datetime.",
        "kotlinx.html.",
        "kotlinx.serialization.",
        "kotlinx.collections.immutable.",
        "javax.xml.datatype.",
        // Micrometer's core measurement API (Timer, Tag, Meter, Counter, Gauge, MeterRegistry) records
        // in-memory only — the actual I/O (scraping, pushing) happens in a separate concrete registry/
        // exporter package (io.micrometer.prometheusmetrics, io.micrometer.registry.otlp, etc.), which
        // stays uncovered and so still counts as a real signal.
        "io.micrometer.core.instrument.",
        // Same reasoning as logging: OpenTelemetry's instrumentation API (spans, metrics, baggage,
        // context propagation) wraps a call for observability — it's instrumentation, not the I/O
        // itself. The actual export happens in a separate io.opentelemetry.exporter.* package, which
        // stays uncovered and so still counts as a real signal.
        "io.opentelemetry.api.",
        "io.opentelemetry.context.",
    )

    // Logging is used everywhere and, for the purposes of adapter classification, is treated as a
    // reliable no-op: it can technically write somewhere eventually, but the probability of it
    // being what actually distinguishes an adapter from a leaf class is close to zero, and
    // treating "calls a logger" as an I/O signal would flag almost every class in a codebase.
    private val LOGGING_PACKAGES = setOf(
        "org.slf4j.",
        "net.logstash.logback.",
    )

    private val NON_ADAPTER_SIGNAL_PACKAGES = STDLIB_PACKAGES + VALUE_LIBRARY_PACKAGES + LOGGING_PACKAGES + JDK_MARKER_INTERFACES

    // A project can extend this list per-project via cnav-config.json's rings.frameworkPackages, for
    // internal/private I/O client libraries that could never belong in a built-in, cross-project list.
    private val FRAMEWORK_PACKAGES = setOf(
        "io.ktor", "org.springframework", "jakarta.", "javax.",
        "org.jetbrains.exposed", "org.hibernate",
        "io.quarkus", "io.vertx",
        "org.apache.http", "okhttp3", "java.net.http",
        "java.sql", "javax.sql",
        "com.zaxxer.hikari",
        "org.eclipse.microprofile",
        "io.grpc", "net.devh.boot.grpc",
    )
}
