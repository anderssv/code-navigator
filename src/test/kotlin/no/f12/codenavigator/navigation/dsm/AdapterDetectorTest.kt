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

        val reasons = AdapterDetector.detect(
            projectClasses = setOf(service, store),
            projectDeps = listOf(dep(service.value, store.value)),
            externalDeps = listOf(dep(store.value, "redis.clients.jedis.Jedis")),
        )

        assertEquals(AdapterReason.SINK_WITH_EXTERNAL_CALLS, reasons[store])
        assertNull(reasons[service])
    }

    @Test
    fun `a class wired only by a composition root and calling a library is an entry point adapter`() {
        val root = ClassName("com.app.ApplicationKt")
        val routes = ClassName("com.app.web.PollRoutesKt")
        val service = ClassName("com.app.polls.PollService")

        val reasons = AdapterDetector.detect(
            projectClasses = setOf(root, routes, service),
            projectDeps = listOf(dep(root.value, routes.value), dep(routes.value, service.value)),
            externalDeps = listOf(dep(routes.value, "com.acme.microweb.Router")),
            compositionRoots = setOf(root),
        )

        assertEquals(AdapterReason.UNCALLED_ENTRY_POINT, reasons[routes])
    }

    @Test
    fun `a class nothing references and no composition root wires is not an adapter`() {
        val orphan = ClassName("com.app.leftovers.OldImporterKt")
        val service = ClassName("com.app.polls.PollService")

        val reasons = AdapterDetector.detect(
            projectClasses = setOf(orphan, service),
            projectDeps = listOf(dep(orphan.value, service.value)),
            externalDeps = listOf(dep(orphan.value, "com.acme.microweb.Router")),
            compositionRoots = emptySet(),
        )

        assertEquals(null, reasons[orphan])
    }

    @Test
    fun `a framework type in a signature outranks one used only in a body`() {
        val renderer = ClassName("com.app.web.HtmlRenderUtilsKt")
        val helper = ClassName("com.app.web.CalendarComponentsKt")
        val caller = ClassName("com.app.web.PageKt")

        val reasons = AdapterDetector.detect(
            projectClasses = setOf(renderer, helper, caller),
            projectDeps = listOf(dep(caller.value, renderer.value), dep(caller.value, helper.value), dep(renderer.value, helper.value), dep(helper.value, caller.value)),
            externalDeps = listOf(
                dep(renderer.value, "io.ktor.server.application.ApplicationCall"),
                dep(helper.value, "io.ktor.server.application.ApplicationCall"),
            ),
            signatureTypes = mapOf(renderer to setOf(ClassName("io.ktor.server.application.ApplicationCall"))),
        )

        assertEquals(AdapterReason.FRAMEWORK_SIGNATURE, reasons[renderer])
        assertEquals(AdapterReason.FRAMEWORK_TYPE, reasons[helper])
    }

    @Test
    fun `a framework reference outranks a topological signal`() {
        val routes = ClassName("com.app.web.PollRoutesKt")
        val service = ClassName("com.app.polls.PollService")

        val reasons = AdapterDetector.detect(
            projectClasses = setOf(routes, service),
            projectDeps = listOf(dep(routes.value, service.value)),
            externalDeps = listOf(dep(routes.value, "io.ktor.server.routing.Route")),
        )

        assertEquals(AdapterReason.FRAMEWORK_TYPE, reasons[routes])
    }

    @Test
    fun `a sink with no external dependency is not an adapter`() {
        val service = ClassName("com.app.polls.PollService")
        val value = ClassName("com.app.domain.Money")

        val reasons = AdapterDetector.detect(
            projectClasses = setOf(service, value),
            projectDeps = listOf(dep(service.value, value.value)),
            externalDeps = emptyList(),
        )

        assertEquals(emptyMap(), reasons)
    }

    @Test
    fun `a class whose only external references are the standard library is not an adapter`() {
        val service = ClassName("com.app.polls.PollService")
        val poll = ClassName("com.app.domain.Poll")

        val reasons = AdapterDetector.detect(
            projectClasses = setOf(service, poll),
            projectDeps = listOf(dep(service.value, poll.value)),
            externalDeps = listOf(
                dep(poll.value, "java.lang.String"),
                dep(poll.value, "java.util.List"),
                dep(poll.value, "kotlin.jvm.internal.Intrinsics"),
            ),
        )

        assertEquals(emptyMap(), reasons)
    }

    @Test
    fun `a standard library reference does not mask a real framework reference`() {
        val repo = ClassName("com.app.infra.PollsRepositoryImpl")

        val reasons = AdapterDetector.detect(
            projectClasses = setOf(repo),
            projectDeps = emptyList(),
            externalDeps = listOf(
                dep(repo.value, "java.lang.String"),
                dep(repo.value, "java.sql.Connection"),
            ),
        )

        assertEquals(AdapterReason.FRAMEWORK_TYPE, reasons[repo])
    }
}
