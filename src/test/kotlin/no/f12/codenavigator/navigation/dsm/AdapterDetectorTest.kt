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
