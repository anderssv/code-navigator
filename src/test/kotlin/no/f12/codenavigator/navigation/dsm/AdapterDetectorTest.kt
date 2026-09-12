package no.f12.codenavigator.navigation.dsm

import no.f12.codenavigator.navigation.types.AnnotationName
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
    fun `a Spring REST controller is a framework entry point even with no framework type in its signature`() {
        // A real, reproducible case: a Spring MVC/REST controller whose fields, parameters and return
        // types are all project classes (services, request/response DTOs) has nothing for
        // SignatureTypeScanner or the topological rules to catch -- @RestController/@RequestMapping
        // are annotations, which that scanner deliberately never visits. Without a dedicated check, a
        // controller like this is invisible to every existing signal, and worse: since nothing in the
        // project's own bytecode calls it (the framework dispatches to it via reflection), it looks
        // structurally identical to a composition root instead of the driving adapter it actually is.
        val controller = ClassName("com.app.web.ArticleController")
        val service = ClassName("com.app.domain.ArticleService")

        val findings = AdapterDetector.detect(
            projectClasses = setOf(controller, service),
            projectDeps = listOf(dep(controller.value, service.value)),
            externalDeps = emptyList(),
            classAnnotations = mapOf(controller to setOf(AnnotationName("org.springframework.web.bind.annotation.RestController"))),
        )

        assertEquals(AdapterReason.FRAMEWORK_ENTRY_POINT_ANNOTATION, findings[controller]?.reason)
    }

    @Test
    fun `a plain class annotated with an unrelated marker is not a framework entry point`() {
        val plain = ClassName("com.app.domain.ArticleValidator")

        val findings = AdapterDetector.detect(
            projectClasses = setOf(plain),
            projectDeps = emptyList(),
            externalDeps = emptyList(),
            classAnnotations = mapOf(plain to setOf(AnnotationName("org.springframework.stereotype.Component"))),
        )

        assertNull(findings[plain], "@Component is the generic stereotype most domain/service beans carry -- treating it as an entry-point signal would misclassify ordinary business logic")
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
    fun `a Quarkus reactive messaging emitter is a real framework signal`() {
        // A real, reproducible case: a class holding a field of type
        // io.smallrye.reactive.messaging.MutinyEmitter and genuinely calling send/sendAndForget on it
        // (a real Kafka/AMQP publish) had no signal to be classified by at all -- neither
        // FRAMEWORK_PACKAGES nor any exclusion list mentioned io.smallrye, so a class that performs a
        // real outbound message send was silently invisible to adapter classification.
        val service = ClassName("com.app.fights.FightService")
        val emitterField = ClassName("com.app.fights.schema.Fight")

        val findings = AdapterDetector.detect(
            projectClasses = setOf(service, emitterField),
            projectDeps = emptyList(),
            externalDeps = listOf(dep(service.value, "io.smallrye.reactive.messaging.MutinyEmitter")),
        )

        assertEquals(AdapterReason.FRAMEWORK_TYPE, findings[service]?.reason, "a reactive messaging emitter genuinely publishes messages -- real I/O")
    }

    @Test
    fun `an Avro generated schema value class is not a sink adapter`() {
        // A real, reproducible case: org.apache.avro.specific.SpecificRecordBase/SpecificRecordBuilderBase
        // (the supertype of every Avro-codegen'd class) and org.apache.avro.message.* (the in-class
        // convenience encoder/decoder every generated class carries as static fields) carry no I/O of
        // their own -- the class is a pure wire-format value object, structurally identical to a
        // protobuf-generated message (already excluded via com.google.protobuf.). The actual I/O
        // boundary is the surrounding messaging/Kafka connector, a separate package.
        val service = ClassName("com.app.polls.PollService")
        val schemaClass = ClassName("com.app.fights.schema.Fight")

        val findings = AdapterDetector.detect(
            projectClasses = setOf(service, schemaClass),
            projectDeps = listOf(dep(service.value, schemaClass.value)),
            externalDeps = listOf(
                dep(schemaClass.value, "org.apache.avro.specific.SpecificRecordBase"),
                dep(schemaClass.value, "org.apache.avro.message.BinaryMessageEncoder"),
                dep(schemaClass.value, "org.apache.avro.message.BinaryMessageDecoder"),
            ),
        )

        assertEquals(null, findings[schemaClass], "an Avro-generated schema class is a pure value object, same category as a protobuf-generated message")
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
    fun `Quarkus's own logging facade does not outrank the real reason a class is an adapter`() {
        // A real, reproducible case: io.quarkus.logging.Log matched the bare "io.quarkus" framework
        // prefix (nothing in LOGGING_PACKAGES excluded it), so a class whose ONLY other dependency is
        // a project interface (invisible to externalDeps, since it's project-internal) got classified
        // as FRAMEWORK_TYPE with "io.quarkus.logging.Log" as evidence -- a misleading reason that made
        // it look like logging caused the classification, when the class doing real work (dispatching
        // to a REST client port) has no other external signal to report at all.
        val service = ClassName("com.app.fights.HeroClient")

        val findings = AdapterDetector.detect(
            projectClasses = setOf(service),
            projectDeps = emptyList(),
            externalDeps = listOf(dep(service.value, "io.quarkus.logging.Log")),
        )

        assertEquals(null, findings[service], "Quarkus's own logging facade should be excluded the same way org.slf4j is")
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
    fun `a predicate over a JAX-RS exception type is not a sink adapter`() {
        // A real, reproducible case: a stateless Predicate<Throwable> inspecting an exception's HTTP
        // status code (e.g. "is this a 404?") references jakarta.ws.rs.WebApplicationException purely
        // as a value/marker type -- it performs no I/O of its own, unlike a real jakarta.ws.rs client
        // type (WebTarget, Client). Deliberately narrow: jakarta.ws.rs itself stays a real signal
        // elsewhere (WebTarget, Client), this excludes only the exception type.
        val service = ClassName("com.app.polls.PollService")
        val predicate = ClassName("com.app.client.Is404Exception")

        val findings = AdapterDetector.detect(
            projectClasses = setOf(service, predicate),
            projectDeps = listOf(dep(service.value, predicate.value)),
            externalDeps = listOf(dep(predicate.value, "jakarta.ws.rs.WebApplicationException")),
        )

        assertEquals(null, findings[predicate], "WebApplicationException is inspected as a value, not used to perform I/O")
    }

    @Test
    fun `reading the status off a JAX-RS Response is not a sink adapter`() {
        // Same shape one hop further: after excluding WebApplicationException, a class reading
        // response.getStatus() off jakarta.ws.rs.core.Response -- obtained purely from an exception,
        // never constructed or sent -- still isn't performing I/O. Response/Response.Builder are
        // value/builder objects (closer to JsonNode than to ObjectMapper): the actual HTTP write
        // happens later, inside the JAX-RS runtime, not through this type directly. A resource class
        // that genuinely answers requests is already flagged via other signals (its @Path annotation,
        // or a real client's WebTarget/Client), so this exclusion doesn't hide those.
        val service = ClassName("com.app.polls.PollService")
        val predicate = ClassName("com.app.client.Is404Exception")

        val findings = AdapterDetector.detect(
            projectClasses = setOf(service, predicate),
            projectDeps = listOf(dep(service.value, predicate.value)),
            externalDeps = listOf(dep(predicate.value, "jakarta.ws.rs.core.Response")),
        )

        assertEquals(null, findings[predicate], "Response is read as a value here, not constructed or sent")
    }

    @Test
    fun `a data class whose only field type is Jackson JsonNode is not a sink adapter`() {
        val service = ClassName("com.app.polls.PollService")
        val dto = ClassName("com.app.dto.SessionAndPublicKey")

        val findings = AdapterDetector.detect(
            projectClasses = setOf(service, dto),
            projectDeps = listOf(dep(service.value, dto.value)),
            externalDeps = listOf(dep(dto.value, "com.fasterxml.jackson.databind.JsonNode")),
        )

        assertEquals(null, findings[dto], "JsonNode is a generic JSON tree value holder, not I/O itself — unlike ObjectMapper, the actual marshaling engine")
    }

    @Test
    fun `Jackson ObjectMapper still counts as a real signal, unlike JsonNode`() {
        val service = ClassName("com.app.polls.PollService")
        val diContainer = ClassName("com.app.di.AppDependencies")

        val findings = AdapterDetector.detect(
            projectClasses = setOf(service, diContainer),
            projectDeps = listOf(dep(service.value, diContainer.value)),
            externalDeps = listOf(dep(diContainer.value, "com.fasterxml.jackson.databind.ObjectMapper")),
        )

        assertEquals(AdapterReason.SINK_WITH_EXTERNAL_CALLS, findings[diContainer]?.reason, "ObjectMapper is the actual JSON marshaling engine, a real signal unlike the generic JsonNode tree type")
    }

    @Test
    fun `a class whose only external reference is commons-pool2 GenericObjectPoolConfig is not a sink adapter`() {
        val service = ClassName("com.app.polls.PollService")
        val poolDefaults = ClassName("com.app.cache.RedisPoolDefaultsKt")

        val findings = AdapterDetector.detect(
            projectClasses = setOf(service, poolDefaults),
            projectDeps = listOf(dep(service.value, poolDefaults.value)),
            externalDeps = listOf(dep(poolDefaults.value, "org.apache.commons.pool2.impl.GenericObjectPoolConfig")),
        )

        assertEquals(null, findings[poolDefaults], "GenericObjectPoolConfig only carries pool-tuning properties (maxTotal, minIdle, timeouts), no I/O of its own")
    }

    @Test
    fun `a real commons-pool2 pool type still counts as a real signal, unlike its config class`() {
        val service = ClassName("com.app.polls.PollService")
        val poolWrapper = ClassName("com.app.cache.ConnectionPool")

        val findings = AdapterDetector.detect(
            projectClasses = setOf(service, poolWrapper),
            projectDeps = listOf(dep(service.value, poolWrapper.value)),
            externalDeps = listOf(dep(poolWrapper.value, "org.apache.commons.pool2.impl.GenericObjectPool")),
        )

        assertEquals(AdapterReason.SINK_WITH_EXTERNAL_CALLS, findings[poolWrapper]?.reason, "GenericObjectPool is the actual pooled-resource lifecycle manager, a real signal unlike its config class")
    }

    @Test
    fun `a class whose only external reference is protobuf generated message metadata is not a sink adapter`() {
        // A real, reproducible case: a protoc-generated *OrBuilder interface / file-descriptor holder
        // (com.google.protobuf.MessageOrBuilder, com.google.protobuf.GeneratedFile) carries no I/O of
        // its own -- it's pure message-shape/schema metadata. The actual gRPC transport lives in a
        // separate io.grpc./io.quarkus.grpc. package, which stays a real, uncovered signal.
        val service = ClassName("com.app.fights.FightService")
        val generatedProto = ClassName("com.app.grpc.LocationOrBuilder")

        val findings = AdapterDetector.detect(
            projectClasses = setOf(service, generatedProto),
            projectDeps = listOf(dep(service.value, generatedProto.value)),
            externalDeps = listOf(
                dep(generatedProto.value, "com.google.protobuf.MessageOrBuilder"),
                dep(generatedProto.value, "com.google.protobuf.GeneratedFile"),
            ),
        )

        assertEquals(null, findings[generatedProto], "protobuf's own generated message/descriptor types carry no I/O; the real gRPC transport is a separate package")
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
    fun `java-nio-ByteBuffer usage alone is not a sink adapter`() {
        // A real, reproducible case: Avro-generated toByteBuffer()/fromByteBuffer() helper methods
        // reference java.nio.ByteBuffer -- a pure in-memory byte container, not an I/O channel. Real
        // I/O happens via a java.nio.channels.* channel reading/writing the buffer's bytes elsewhere;
        // ByteBuffer itself never touches a file, socket, or any external resource. Deliberately
        // narrow (exact match): java.nio.file.* / java.nio.channels.* stay real signals.
        val service = ClassName("com.app.polls.PollService")
        val schemaClass = ClassName("com.app.fights.schema.Fight")

        val findings = AdapterDetector.detect(
            projectClasses = setOf(service, schemaClass),
            projectDeps = listOf(dep(service.value, schemaClass.value)),
            externalDeps = listOf(dep(schemaClass.value, "java.nio.ByteBuffer")),
        )

        assertEquals(null, findings[schemaClass], "ByteBuffer is a pure in-memory container, no I/O of its own")
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
