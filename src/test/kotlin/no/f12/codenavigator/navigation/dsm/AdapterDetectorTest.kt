package no.f12.codenavigator.navigation.dsm

import no.f12.codenavigator.navigation.types.ClassName
import no.f12.codenavigator.navigation.types.PackageName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AdapterDetectorTest {

    private fun dep(source: String, target: String) = PackageDependency(
        PackageName(ClassName(source).packageName().value),
        PackageName(ClassName(target).packageName().value),
        ClassName(source),
        ClassName(target),
    )

    @Test
    fun `a called class with no outgoing project calls that talks to a library is a sink adapter`() {
        val service = ClassName("com.app.polls.PollService")
        val store = ClassName("com.app.infra.RedisPollStore")

        val findings = AdapterDetector.detect(
            projectClasses = setOf(service, store),
            projectDeps = listOf(dep(service.value, store.value)),
            externalDeps = listOf(dep(store.value, "redis.clients.jedis.Jedis")),
        )

        assertEquals(AdapterFinding(AdapterReason.SINK_WITH_EXTERNAL_CALLS, ClassName("redis.clients.jedis.Jedis")), findings[store])
        assertNull(findings[service])
    }

    @Test
    fun `a class wired only by a composition root and calling a library is an entry point adapter`() {
        val root = ClassName("com.app.ApplicationKt")
        val routes = ClassName("com.app.web.PollRoutesKt")
        val service = ClassName("com.app.polls.PollService")

        val findings = AdapterDetector.detect(
            projectClasses = setOf(root, routes, service),
            projectDeps = listOf(dep(root.value, routes.value), dep(routes.value, service.value)),
            externalDeps = listOf(dep(routes.value, "com.acme.microweb.Router")),
            compositionRoots = setOf(root),
        )

        assertEquals(AdapterReason.UNCALLED_ENTRY_POINT, findings[routes]?.reason)
        assertEquals(ClassName("com.acme.microweb.Router"), findings[routes]?.evidence)
    }

    @Test
    fun `a class nothing references and no composition root wires is not an adapter`() {
        val orphan = ClassName("com.app.leftovers.OldImporterKt")
        val service = ClassName("com.app.polls.PollService")

        val findings = AdapterDetector.detect(
            projectClasses = setOf(orphan, service),
            projectDeps = listOf(dep(orphan.value, service.value)),
            externalDeps = listOf(dep(orphan.value, "com.acme.microweb.Router")),
            compositionRoots = emptySet(),
        )

        assertEquals(null, findings[orphan])
    }

    @Test
    fun `a framework type in a signature outranks one used only in a body`() {
        val renderer = ClassName("com.app.web.HtmlRenderUtilsKt")
        val helper = ClassName("com.app.web.CalendarComponentsKt")
        val caller = ClassName("com.app.web.PageKt")

        val findings = AdapterDetector.detect(
            projectClasses = setOf(renderer, helper, caller),
            projectDeps = listOf(dep(caller.value, renderer.value), dep(caller.value, helper.value), dep(renderer.value, helper.value), dep(helper.value, caller.value)),
            externalDeps = listOf(
                dep(renderer.value, "io.ktor.server.application.ApplicationCall"),
                dep(helper.value, "io.ktor.server.application.ApplicationCall"),
            ),
            signatureTypes = mapOf(renderer to setOf(ClassName("io.ktor.server.application.ApplicationCall"))),
        )

        assertEquals(AdapterReason.FRAMEWORK_SIGNATURE, findings[renderer]?.reason)
        assertEquals(AdapterReason.FRAMEWORK_TYPE, findings[helper]?.reason)
    }

    @Test
    fun `a project class referencing another project class is not a framework signal, even when the project's own root package matches a framework prefix`() {
        // A real, reproducible case: org.springframework.samples.petclinic (the official Spring Boot
        // sample app) is rooted under "org.springframework" -- the same bare prefix used to detect
        // real Spring framework usage. Without excluding project-internal types first, any class in
        // this project referencing any OTHER class in this project self-matches the framework prefix,
        // since both FQCNs start with "org.springframework" regardless of which class is doing the I/O.
        val owner = ClassName("org.springframework.samples.petclinic.owner.Owner")
        val pet = ClassName("org.springframework.samples.petclinic.owner.Pet")

        val findings = AdapterDetector.detect(
            projectClasses = setOf(owner, pet),
            projectDeps = listOf(dep(owner.value, pet.value)),
            externalDeps = emptyList(),
            signatureTypes = mapOf(owner to setOf(pet)),
        )

        assertEquals(null, findings[owner], "Pet is a project class, not a real Spring framework type -- referencing it must not classify Owner as an adapter")
    }

    @Test
    fun `a framework reference outranks a topological signal`() {
        val routes = ClassName("com.app.web.PollRoutesKt")
        val service = ClassName("com.app.polls.PollService")

        val findings = AdapterDetector.detect(
            projectClasses = setOf(routes, service),
            projectDeps = listOf(dep(routes.value, service.value)),
            externalDeps = listOf(dep(routes.value, "io.ktor.server.routing.Route")),
        )

        assertEquals(AdapterReason.FRAMEWORK_TYPE, findings[routes]?.reason)
    }

    @Test
    fun `a sink with no external dependency is not an adapter`() {
        val service = ClassName("com.app.polls.PollService")
        val value = ClassName("com.app.domain.Money")

        val findings = AdapterDetector.detect(
            projectClasses = setOf(service, value),
            projectDeps = listOf(dep(service.value, value.value)),
            externalDeps = emptyList(),
        )

        assertEquals(emptyMap(), findings)
    }

    @Test
    fun `a class whose only external references are the standard library is not an adapter`() {
        val service = ClassName("com.app.polls.PollService")
        val poll = ClassName("com.app.domain.Poll")

        val findings = AdapterDetector.detect(
            projectClasses = setOf(service, poll),
            projectDeps = listOf(dep(service.value, poll.value)),
            externalDeps = listOf(
                dep(poll.value, "java.lang.String"),
                dep(poll.value, "java.util.List"),
                dep(poll.value, "kotlin.jvm.internal.Intrinsics"),
            ),
        )

        assertEquals(emptyMap(), findings)
    }

    @Test
    fun `a class whose only external reference is javax-xml-datatype is not a sink adapter`() {
        val service = ClassName("com.app.polls.PollService")
        val errorResponse = ClassName("no.bankid.ra.ErrorResponse")

        val findings = AdapterDetector.detect(
            projectClasses = setOf(service, errorResponse),
            projectDeps = listOf(dep(service.value, errorResponse.value)),
            externalDeps = listOf(dep(errorResponse.value, "javax.xml.datatype.XMLGregorianCalendar")),
        )

        assertEquals(null, findings[errorResponse], "javax.xml.datatype is JAXB's pure value type for dates, not I/O — a bare 'javax.' prefix in FRAMEWORK_PACKAGES was too broad")
    }

    @Test
    fun `javax-sql still counts as a real framework signal, unlike javax-xml-datatype`() {
        val service = ClassName("com.app.polls.PollService")
        val dataSource = ClassName("com.app.infra.PooledDataSource")

        val findings = AdapterDetector.detect(
            projectClasses = setOf(service, dataSource),
            projectDeps = listOf(dep(service.value, dataSource.value)),
            externalDeps = listOf(dep(dataSource.value, "javax.sql.DataSource")),
        )

        assertEquals(AdapterReason.FRAMEWORK_TYPE, findings[dataSource]?.reason, "javax.sql is real JDBC I/O and should still count as a framework signal")
    }

    @Test
    fun `a class whose only external reference is a logging library is not a sink adapter`() {
        val service = ClassName("com.app.polls.PollService")
        val retryHelper = ClassName("com.app.RetryKt")

        val findings = AdapterDetector.detect(
            projectClasses = setOf(service, retryHelper),
            projectDeps = listOf(dep(service.value, retryHelper.value)),
            externalDeps = listOf(
                dep(retryHelper.value, "org.slf4j.Logger"),
                dep(retryHelper.value, "net.logstash.logback.argument.StructuredArguments"),
            ),
        )

        assertEquals(null, findings[retryHelper], "logging is ubiquitous and treated as a reliable no-op for boundary-detection purposes, not an I/O signal")
    }

    @Test
    fun `a class whose only external reference is the Micrometer core measurement API is not a sink adapter`() {
        val service = ClassName("com.app.polls.PollService")
        val timerHelper = ClassName("com.app.metrics.TimerHelpersKt")

        val findings = AdapterDetector.detect(
            projectClasses = setOf(service, timerHelper),
            projectDeps = listOf(dep(service.value, timerHelper.value)),
            externalDeps = listOf(dep(timerHelper.value, "io.micrometer.core.instrument.Timer")),
        )

        assertEquals(null, findings[timerHelper], "Timer/Tag/Meter/MeterRegistry are Micrometer's in-memory measurement API — the actual I/O happens in a separate concrete registry/exporter package (e.g. io.micrometer.prometheusmetrics), not here")
    }

    @Test
    fun `a Micrometer exporter package still counts as a real signal, unlike the core measurement API`() {
        val service = ClassName("com.app.polls.PollService")
        val registry = ClassName("com.app.metrics.MetricsConfig")

        val findings = AdapterDetector.detect(
            projectClasses = setOf(service, registry),
            projectDeps = listOf(dep(service.value, registry.value)),
            externalDeps = listOf(dep(registry.value, "io.micrometer.prometheusmetrics.PrometheusMeterRegistry")),
        )

        assertEquals(AdapterReason.SINK_WITH_EXTERNAL_CALLS, findings[registry]?.reason, "the Prometheus exporter is a separate, real I/O-adjacent package from the core measurement API, and stays a signal")
    }

    @Test
    fun `a class whose only external reference is OpenTelemetry tracing is not a sink adapter`() {
        val service = ClassName("com.app.polls.PollService")
        val tracingHelper = ClassName("com.app.cache.RedisTracingKt")

        val findings = AdapterDetector.detect(
            projectClasses = setOf(service, tracingHelper),
            projectDeps = listOf(dep(service.value, tracingHelper.value)),
            externalDeps = listOf(
                dep(tracingHelper.value, "io.opentelemetry.api.trace.Tracer"),
                dep(tracingHelper.value, "io.opentelemetry.api.GlobalOpenTelemetry"),
            ),
        )

        assertEquals(null, findings[tracingHelper], "OpenTelemetry's tracing API wraps calls with spans — it's instrumentation, not I/O itself, same shape as a logging facade")
    }

    @Test
    fun `a class whose only external reference is OpenTelemetry context Scope is not a sink adapter`() {
        val service = ClassName("com.app.polls.PollService")
        val tracingHelper = ClassName("com.app.cache.RedisTracingKt")

        val findings = AdapterDetector.detect(
            projectClasses = setOf(service, tracingHelper),
            projectDeps = listOf(dep(service.value, tracingHelper.value)),
            externalDeps = listOf(dep(tracingHelper.value, "io.opentelemetry.context.Scope")),
        )

        assertEquals(null, findings[tracingHelper], "Scope (returned by span.makeCurrent(), closed to end the span) is the same instrumentation API as Tracer, just a different OpenTelemetry sub-package")
    }

    @Test
    fun `a class referencing the base OpenTelemetry api interface is not a sink adapter`() {
        val service = ClassName("com.app.polls.PollService")
        val tracingHelper = ClassName("com.app.cache.RedisTracingKt")

        val findings = AdapterDetector.detect(
            projectClasses = setOf(service, tracingHelper),
            projectDeps = listOf(dep(service.value, tracingHelper.value)),
            externalDeps = listOf(dep(tracingHelper.value, "io.opentelemetry.api.OpenTelemetry")),
        )

        assertEquals(null, findings[tracingHelper], "the whole io.opentelemetry.api package is the instrumentation API (spans, metrics, baggage, context) — distinct from io.opentelemetry.exporter.*, which stays a real signal")
    }

    @Test
    fun `an OpenTelemetry exporter package still counts as a real signal`() {
        val service = ClassName("com.app.polls.PollService")
        val exporterConfig = ClassName("com.app.observability.OtlpExporterConfig")

        val findings = AdapterDetector.detect(
            projectClasses = setOf(service, exporterConfig),
            projectDeps = listOf(dep(service.value, exporterConfig.value)),
            externalDeps = listOf(dep(exporterConfig.value, "io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter")),
        )

        assertEquals(AdapterReason.SINK_WITH_EXTERNAL_CALLS, findings[exporterConfig]?.reason, "the OTLP exporter sends spans over HTTP — real I/O, unlike the api package")
    }

    @Test
    fun `a standard library reference does not mask a real framework reference`() {
        val repo = ClassName("com.app.infra.PollsRepositoryImpl")

        val findings = AdapterDetector.detect(
            projectClasses = setOf(repo),
            projectDeps = emptyList(),
            externalDeps = listOf(
                dep(repo.value, "java.lang.String"),
                dep(repo.value, "java.sql.Connection"),
            ),
        )

        assertEquals(AdapterReason.FRAMEWORK_TYPE, findings[repo]?.reason)
    }

    @Test
    fun `a class whose only external reference is java-io-Serializable is not a sink adapter`() {
        val service = ClassName("com.app.polls.PollService")
        val baseEntity = ClassName("com.app.model.BaseEntity")

        val findings = AdapterDetector.detect(
            projectClasses = setOf(service, baseEntity),
            projectDeps = listOf(dep(service.value, baseEntity.value)),
            externalDeps = listOf(dep(baseEntity.value, "java.io.Serializable")),
        )

        assertEquals(null, findings[baseEntity], "java.io.Serializable is a pure marker interface with no I/O of its own, unlike a real java.io.* I/O type")
    }

    @Test
    fun `a class whose only external references are pure value or DSL libraries is not a sink adapter`() {
        val service = ClassName("com.app.polls.PollService")
        val dateHelper = ClassName("com.app.polls.calendar.DateFormattingKt")

        val findings = AdapterDetector.detect(
            projectClasses = setOf(service, dateHelper),
            projectDeps = listOf(dep(service.value, dateHelper.value)),
            externalDeps = listOf(
                dep(dateHelper.value, "kotlinx.datetime.LocalDate"),
                dep(dateHelper.value, "kotlinx.html.FlowContent"),
                dep(dateHelper.value, "kotlinx.serialization.Serializable"),
            ),
        )

        assertEquals(null, findings[dateHelper], "kotlinx.datetime/kotlinx.html/kotlinx.serialization are value/DSL libraries, not I/O adapters")
    }

    @Test
    fun `a real infra client library still triggers a sink adapter alongside value library usage`() {
        val service = ClassName("com.app.polls.PollService")
        val store = ClassName("com.app.infra.RedisPollStore")

        val findings = AdapterDetector.detect(
            projectClasses = setOf(service, store),
            projectDeps = listOf(dep(service.value, store.value)),
            externalDeps = listOf(
                dep(store.value, "redis.clients.jedis.Jedis"),
                dep(store.value, "kotlinx.datetime.LocalDate"),
            ),
        )

        assertEquals(AdapterReason.SINK_WITH_EXTERNAL_CALLS, findings[store]?.reason, "a real infra client (Jedis) should still count even alongside value-library usage")
    }

    @Test
    fun `a class whose only external reference is a core JDK crypto or hashing type is not a sink adapter`() {
        val service = ClassName("com.app.polls.PollService")
        val gravatarUtils = ClassName("com.app.web.GravatarUtilsKt")

        val findings = AdapterDetector.detect(
            projectClasses = setOf(service, gravatarUtils),
            projectDeps = listOf(dep(service.value, gravatarUtils.value)),
            externalDeps = listOf(
                dep(gravatarUtils.value, "java.security.MessageDigest"),
                dep(gravatarUtils.value, "java.nio.charset.Charset"),
            ),
        )

        assertEquals(null, findings[gravatarUtils], "java.security/java.nio.charset are core JDK stdlib (hashing/encoding), not I/O adapter signals")
    }

    @Test
    fun `java nio file usage still counts as a real IO signal, unlike java nio charset`() {
        val service = ClassName("com.app.polls.PollService")
        val fileWatcher = ClassName("com.app.web.FileWatcher")

        val findings = AdapterDetector.detect(
            projectClasses = setOf(service, fileWatcher),
            projectDeps = listOf(dep(service.value, fileWatcher.value)),
            externalDeps = listOf(
                dep(fileWatcher.value, "java.nio.file.WatchService"),
            ),
        )

        assertEquals(AdapterReason.SINK_WITH_EXTERNAL_CALLS, findings[fileWatcher]?.reason, "java.nio.file is real filesystem I/O and should still count as an adapter signal")
    }

    @Test
    fun `a sink finding carries the offending external type as evidence`() {
        val service = ClassName("com.app.polls.PollService")
        val idGen = ClassName("com.app.domain.PollId")

        val findings = AdapterDetector.detect(
            projectClasses = setOf(service, idGen),
            projectDeps = listOf(dep(service.value, idGen.value)),
            externalDeps = listOf(dep(idGen.value, "io.viascom.nanoid.NanoId")),
        )

        assertEquals(ClassName("io.viascom.nanoid.NanoId"), findings[idGen]?.evidence)
    }

    @Test
    fun `extraValuePackages lets a project extend the value library exclusion list`() {
        val service = ClassName("com.app.polls.PollService")
        val idGen = ClassName("com.app.domain.PollId")

        val findings = AdapterDetector.detect(
            projectClasses = setOf(service, idGen),
            projectDeps = listOf(dep(service.value, idGen.value)),
            externalDeps = listOf(dep(idGen.value, "io.viascom.nanoid.NanoId")),
            extraValuePackages = setOf("io.viascom.nanoid."),
        )

        assertEquals(null, findings[idGen], "a project-configured value package should suppress the sink signal")
    }

    @Test
    fun `extraFrameworkPackages lets a project extend the framework detection list`() {
        val service = ClassName("com.app.polls.PollService")
        val client = ClassName("com.app.infra.InternalQueueClient")

        val findings = AdapterDetector.detect(
            projectClasses = setOf(service, client),
            projectDeps = listOf(dep(service.value, client.value)),
            externalDeps = listOf(dep(client.value, "com.acme.internalqueue.QueueClient")),
            extraFrameworkPackages = setOf("com.acme.internalqueue."),
        )

        assertEquals(AdapterReason.FRAMEWORK_TYPE, findings[client]?.reason, "a project-configured framework package should be detected the same as a built-in one")
        assertEquals(ClassName("com.acme.internalqueue.QueueClient"), findings[client]?.evidence)
    }
}
