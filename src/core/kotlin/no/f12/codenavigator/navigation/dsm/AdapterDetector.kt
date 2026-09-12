package no.f12.codenavigator.navigation.dsm

import no.f12.codenavigator.navigation.types.AnnotationName
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
        classAnnotations: Map<ClassName, Set<AnnotationName>> = emptyMap(),
    ): Map<ClassName, AdapterFinding> =
        projectClasses
            .mapNotNull { cls ->
                findingFor(cls, projectClasses, projectDeps, externalDeps, signatureTypes, compositionRoots, extraValuePackages, extraFrameworkPackages, classAnnotations)
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
        classAnnotations: Map<ClassName, Set<AnnotationName>> = emptyMap(),
    ): Map<ClassName, AdapterFinding> =
        projectClasses
            .mapNotNull { cls -> frameworkFindingFor(cls, projectClasses, externalDeps, signatureTypes, extraFrameworkPackages, classAnnotations)?.let { cls to it } }
            .toMap()

    private fun frameworkFindingFor(
        cls: ClassName,
        projectClasses: Set<ClassName>,
        externalDeps: List<PackageDependency>,
        signatureTypes: Map<ClassName, Set<ClassName>>,
        extraFrameworkPackages: Set<String>,
        classAnnotations: Map<ClassName, Set<AnnotationName>>,
    ): AdapterFinding? {
        // A known framework entry-point annotation (@RestController, @Controller, JAX-RS @Path) is
        // checked first and independent of everything else: it's a direct, unambiguous signal that the
        // framework dispatches to this class via reflection, regardless of whether its fields/parameters/
        // return types happen to be project classes. Without this, a controller whose signature is
        // entirely project DTOs and services is invisible to every other check, and — since nothing in
        // the project's own bytecode calls it — looks structurally identical to a composition root
        // instead of the driving adapter it actually is.
        classAnnotations[cls].orEmpty().firstOrNull { it.value in ENTRY_POINT_ANNOTATIONS }?.let {
            return AdapterFinding(AdapterReason.FRAMEWORK_ENTRY_POINT_ANNOTATION, ClassName(it.value))
        }
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
        classAnnotations: Map<ClassName, Set<AnnotationName>>,
    ): AdapterFinding? {
        frameworkFindingFor(cls, projectClasses, externalDeps, signatureTypes, extraFrameworkPackages, classAnnotations)?.let { return it }


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
        // Value libraries, logging, and exact-match value types are a carve-out that beats even a
        // broad framework prefix (e.g. bare "javax."): javax.xml.datatype is JAXB's pure date value
        // type, no I/O of its own, but it would otherwise match "javax." — the same broad prefix that
        // correctly covers javax.net.ssl (TLS), javax.xml.parsers (XML parsing), and
        // javax.security.auth (certificates) elsewhere in real code. Narrowing "javax." itself risks
        // silently losing those real signals; excluding known non-I/O types first is safer and more
        // precise.
        if (type.value in EXACT_VALUE_TYPES) return false
        if ((VALUE_LIBRARY_PACKAGES + LOGGING_PACKAGES).any { type.value.startsWith(it) }) return false
        return (FRAMEWORK_PACKAGES + extraFrameworkPackages).any { prefix -> type.value.startsWith(prefix) }
    }

    private val STDLIB_PACKAGES = setOf(
        "java.lang.", "java.util.", "java.math.", "java.time.", "java.text.",
        "java.security.", "java.nio.charset.",
        "kotlin.", "kotlinx.coroutines.",
        "org.jetbrains.annotations.",
    )

    // Exact-match value types (not prefixes): each one is a specific class known to carry no I/O of
    // its own, even though it comes from a library/package that otherwise legitimately signals an
    // adapter. Deliberately narrow — the containing package is NOT excluded, since it also contains
    // real adapter-relevant types this must not mask:
    //   - java.io.Serializable: a JDK marker interface (implementing it declares eligibility for a
    //     JDK mechanism, performs no I/O) — java.io.File/InputStream stay real signals.
    //   - com.fasterxml.jackson.databind.JsonNode: a generic JSON tree value holder — ObjectMapper
    //     (the actual marshaling engine) stays a real signal.
    //   - org.apache.commons.pool2.impl.GenericObjectPoolConfig: pure pool-tuning config properties
    //     (maxTotal, minIdle, timeouts) — GenericObjectPool/PooledObjectFactory (the actual pooled
    //     resource lifecycle) stay real signals.
    private val EXACT_VALUE_TYPES = setOf(
        "java.io.Serializable",
        "com.fasterxml.jackson.databind.JsonNode",
        "org.apache.commons.pool2.impl.GenericObjectPoolConfig",
        // jakarta.ws.rs.WebApplicationException: a pure exception/value type inspected for its status
        // code (e.g. "is this a 404?") -- no I/O of its own, unlike a real jakarta.ws.rs client type
        // (WebTarget, Client), which stay real signals.
        "jakarta.ws.rs.WebApplicationException",
        "javax.ws.rs.WebApplicationException",
        // jakarta.ws.rs.core.Response: a value/builder object (closer to JsonNode than to
        // ObjectMapper) -- the actual HTTP write happens later, inside the JAX-RS runtime, not
        // through this type directly. A resource class that genuinely answers requests is already
        // flagged via other signals (its @Path annotation, or a real client's WebTarget/Client).
        "jakarta.ws.rs.core.Response",
        "javax.ws.rs.core.Response",
        // java.nio.ByteBuffer: a pure in-memory byte container, no I/O of its own -- real I/O happens
        // via a java.nio.channels.* channel reading/writing it elsewhere. java.nio.file./
        // java.nio.channels. stay real signals; this is narrower than either.
        "java.nio.ByteBuffer",
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
        // Protobuf's generated message/descriptor infrastructure (*OrBuilder interfaces, the
        // per-.proto GeneratedFile descriptor holder) is pure message-shape/schema metadata with no
        // I/O of its own -- the actual gRPC transport lives in a separate io.grpc./io.quarkus.grpc.
        // package, which stays a real, uncovered signal.
        "com.google.protobuf.",
        // Avro's generated-schema infrastructure (SpecificRecordBase/SpecificRecordBuilderBase, the
        // per-class BinaryMessageEncoder/Decoder every generated record carries as static
        // convenience fields) is pure wire-format value/codec metadata, no I/O of its own --
        // structurally the same category as the protobuf case above. The actual I/O boundary is the
        // surrounding messaging/Kafka connector (a separate package), which stays a real signal.
        "org.apache.avro.",
    )

    // Logging is used everywhere and, for the purposes of adapter classification, is treated as a
    // reliable no-op: it can technically write somewhere eventually, but the probability of it
    // being what actually distinguishes an adapter from a leaf class is close to zero, and
    // treating "calls a logger" as an I/O signal would flag almost every class in a codebase.
    private val LOGGING_PACKAGES = setOf(
        "org.slf4j.",
        "net.logstash.logback.",
        // Quarkus's own logging facade (io.quarkus.logging.Log) -- without this, it fell through to
        // the bare "io.quarkus" framework prefix and outranked the actual reason a class was an
        // adapter, since a logging call is often the only body-level reference visible when the real
        // dependency is on a project-internal port interface (invisible to externalDeps).
        "io.quarkus.logging.",
        "org.jboss.logging.",
    )

    private val NON_ADAPTER_SIGNAL_PACKAGES = STDLIB_PACKAGES + VALUE_LIBRARY_PACKAGES + LOGGING_PACKAGES + EXACT_VALUE_TYPES

    // A project can extend this list per-project via cnav-config.json's rings.frameworkPackages, for
    // internal/private I/O client libraries that could never belong in a built-in, cross-project list.
    // Class-level annotations that mark a class as a framework entry point regardless of what its
    // signature looks like — the framework dispatches to these via reflection (an HTTP router, a
    // JAX-RS resource locator), so nothing in the project's own bytecode ever calls them and their
    // fields/parameters/return types are frequently all project classes (DTOs, services), invisible
    // to every other signal. Deliberately narrow: only annotations that exist *specifically* to mark
    // an HTTP/RPC entry point. Generic stereotypes like @Component/@Service/@Bean are excluded on
    // purpose — those mark ordinary dependency-injected beans, the overwhelming majority of which are
    // domain/service code, not adapters.
    private val ENTRY_POINT_ANNOTATIONS = setOf(
        "org.springframework.web.bind.annotation.RestController",
        "org.springframework.stereotype.Controller",
        "jakarta.ws.rs.Path",
        "javax.ws.rs.Path",
    )

    private val FRAMEWORK_PACKAGES = setOf(
        "io.ktor", "org.springframework", "jakarta.", "javax.",
        "org.jetbrains.exposed", "org.hibernate",
        "io.quarkus", "io.vertx",
        "org.apache.http", "okhttp3", "java.net.http",
        "java.sql", "javax.sql",
        "com.zaxxer.hikari",
        "org.eclipse.microprofile",
        "io.grpc", "net.devh.boot.grpc",
        // Quarkus/SmallRye reactive messaging (MutinyEmitter, @Channel/@Incoming consumers) is the
        // Kafka/AMQP publish-subscribe boundary -- a class holding one of these and genuinely calling
        // send/sendAndForget performs real outbound I/O.
        "io.smallrye.reactive.messaging",
    )
}
