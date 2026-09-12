package no.f12.codenavigator.navigation.dsm

import no.f12.codenavigator.navigation.types.ClassName
import no.f12.codenavigator.navigation.types.PackageName
import kotlin.test.Test
import kotlin.test.assertEquals

class RingGraphBuilderTest {

    private fun dep(source: String, target: String) = PackageDependency(
        PackageName(ClassName(source).packageName().value),
        PackageName(ClassName(target).packageName().value),
        ClassName(source),
        ClassName(target),
    )

    @Test
    fun `a class referencing a framework package is an I O class`() {
        val impl = ClassName("com.app.infra.PollsRepositoryImpl")
        val poll = ClassName("com.app.domain.Poll")

        val graph = RingGraphBuilder.build(
            projectClasses = setOf(impl, poll),
            projectDeps = emptyList(),
            externalDeps = listOf(dep(impl.value, "org.jetbrains.exposed.sql.Table")),
            classKinds = emptyMap(),
            supertypes = emptyList(),
        )

        assertEquals(setOf(impl), graph.ioClasses)
        assertEquals(mapOf(impl to AdapterReason.FRAMEWORK_TYPE), graph.adapterReasons)
        assertEquals(mapOf(impl to ClassName("org.jetbrains.exposed.sql.Table")), graph.adapterEvidence)
    }

    @Test
    fun `a mid-graph class referencing a non-framework external library is not an I O class`() {
        val helper = ClassName("com.app.domain.Calendar")
        val caller = ClassName("com.app.polls.PollPage")
        val callee = ClassName("com.app.domain.Day")

        val graph = RingGraphBuilder.build(
            projectClasses = setOf(helper, caller, callee),
            projectDeps = listOf(dep(caller.value, helper.value), dep(helper.value, callee.value)),
            externalDeps = listOf(dep(helper.value, "kotlinx.html.DIV")),
            classKinds = emptyMap(),
            supertypes = emptyList(),
        )

        assertEquals(emptySet(), graph.ioClasses)
    }

    @Test
    fun `an interface is mapped to its project implementors`() {
        val port = ClassName("com.app.polls.PollsRepository")
        val impl = ClassName("com.app.infra.PollsRepositoryImpl")
        val fake = ClassName("com.app.polls.PollsRepositoryFake")

        val graph = RingGraphBuilder.build(
            projectClasses = setOf(port, impl, fake),
            projectDeps = emptyList(),
            externalDeps = emptyList(),
            classKinds = mapOf(port to ClassKind.INTERFACE, impl to ClassKind.CONCRETE, fake to ClassKind.CONCRETE),
            supertypes = listOf(
                StructuralSupertypeInfo(impl, port),
                StructuralSupertypeInfo(fake, port),
            ),
        )

        assertEquals(setOf(port), graph.interfaces)
        assertEquals(mapOf(port to setOf(impl, fake)), graph.implementedBy)
    }
    @Test
    fun `dependsOn is built from project dependencies`() {
        val service = ClassName("com.app.polls.PollService")
        val port = ClassName("com.app.polls.PollsRepository")

        val graph = RingGraphBuilder.build(
            projectClasses = setOf(service, port),
            projectDeps = listOf(dep(service.value, port.value)),
            externalDeps = emptyList(),
            classKinds = emptyMap(),
            supertypes = emptyList(),
        )

        assertEquals(mapOf(service to setOf(port)), graph.dependsOn)
    }

    @Test
    fun `composition roots combine configured entries with detected ones`() {
        val detectedRoot = ClassName("com.app.ApplicationKt")
        val configuredRoot = ClassName("com.app.TestWiring")
        val impl = ClassName("com.app.infra.PollsRepositoryImpl")

        val graph = RingGraphBuilder.build(
            projectClasses = setOf(detectedRoot, configuredRoot, impl),
            projectDeps = listOf(dep(detectedRoot.value, impl.value)),
            externalDeps = listOf(dep(impl.value, "java.sql.Connection")),
            classKinds = emptyMap(),
            supertypes = emptyList(),
            configuredCompositionRoots = setOf(configuredRoot),
        )

        assertEquals(setOf(detectedRoot, configuredRoot), graph.compositionRoots)
    }

    @Test
    fun `extraValuePackages suppresses a sink classification for a configured package prefix`() {
        val service = ClassName("com.app.polls.PollService")
        val idGen = ClassName("com.app.domain.PollId")

        val graph = RingGraphBuilder.build(
            projectClasses = setOf(service, idGen),
            projectDeps = listOf(dep(service.value, idGen.value)),
            externalDeps = listOf(dep(idGen.value, "io.viascom.nanoid.NanoId")),
            classKinds = emptyMap(),
            supertypes = emptyList(),
            extraValuePackages = setOf("io.viascom.nanoid."),
        )

        assertEquals(emptySet(), graph.ioClasses)
    }

    @Test
    fun `extraFrameworkPackages classifies a configured package prefix as a framework adapter`() {
        val service = ClassName("com.app.polls.PollService")
        val client = ClassName("com.app.infra.InternalQueueClient")

        val graph = RingGraphBuilder.build(
            projectClasses = setOf(service, client),
            projectDeps = listOf(dep(service.value, client.value)),
            externalDeps = listOf(dep(client.value, "com.acme.internalqueue.QueueClient")),
            classKinds = emptyMap(),
            supertypes = emptyList(),
            extraFrameworkPackages = setOf("com.acme.internalqueue."),
        )

        assertEquals(setOf(client), graph.ioClasses)
        assertEquals(AdapterReason.FRAMEWORK_TYPE, graph.adapterReasons[client])
        assertEquals(ClassName("com.acme.internalqueue.QueueClient"), graph.adapterEvidence[client])
    }
}
