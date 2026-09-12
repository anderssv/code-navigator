package no.f12.codenavigator.navigation.dsm

import no.f12.codenavigator.navigation.types.ClassName
import kotlin.test.Test
import kotlin.test.assertEquals

class RingConfigOverridesTest {

    private val port = ClassName("com.app.polls.PollsRepository")
    private val impl = ClassName("com.app.infra.PollsRepositoryImpl")
    private val renderer = ClassName("com.app.web.HtmlRenderUtils")

    private val graph = RingGraph(
        classes = setOf(port, impl, renderer),
        interfaces = setOf(port),
        implementedBy = mapOf(port to setOf(impl)),
        ioClasses = setOf(impl),
        adapterReasons = mapOf(impl to AdapterReason.FRAMEWORK_TYPE),
    )

    @Test
    fun `a configured adapter pattern marks matching classes as adapters`() {
        val result = RingConfigOverrides.apply(graph, RingsConfig(adapters = listOf("com.app.web.*")))

        assertEquals(setOf(impl, renderer), result.graph.ioClasses)
        assertEquals(AdapterReason.CONFIGURED, result.graph.adapterReasons[renderer])
    }

    @Test
    fun `a notAdapters pattern removes a class from the adapter set`() {
        val result = RingConfigOverrides.apply(graph, RingsConfig(notAdapters = listOf("*Impl")))

        assertEquals(emptySet(), result.graph.ioClasses)
        assertEquals(null, result.graph.adapterReasons[impl])
    }

    @Test
    fun `a configured composition root is added to the graph`() {
        val result = RingConfigOverrides.apply(
            graph,
            RingsConfig(compositionRoots = listOf("com.app.web.HtmlRenderUtils")),
        )

        assertEquals(setOf(renderer), result.graph.compositionRoots)
    }

    @Test
    fun `a directive that matches no class is reported as unhonoured`() {
        val result = RingConfigOverrides.apply(
            graph,
            RingsConfig(
                adapters = listOf("com.app.nonexistent.*"),
                compositionRoots = listOf("com.app.GoneKt"),
            ),
        )

        assertEquals(
            listOf(
                UnhonouredDirective(DirectiveKind.ADAPTER, "com.app.nonexistent.*"),
                UnhonouredDirective(DirectiveKind.COMPOSITION_ROOT, "com.app.GoneKt"),
            ),
            result.unhonoured.sortedBy { it.kind.name },
        )
    }

    @Test
    fun `a notCompositionRoots pattern removes a class from the detected composition roots`() {
        val root = ClassName("com.app.web.OwnerController")
        val graphWithRoot = graph.copy(
            classes = graph.classes + root,
            compositionRoots = setOf(root),
            compositionRootEvidence = mapOf(root to impl),
        )

        val result = RingConfigOverrides.apply(graphWithRoot, RingsConfig(notCompositionRoots = listOf("com.app.web.OwnerController")))

        assertEquals(emptySet(), result.graph.compositionRoots)
        assertEquals(null, result.graph.compositionRootEvidence[root])
    }

    @Test
    fun `a notCompositionRoots directive that matches no class is reported as unhonoured`() {
        val result = RingConfigOverrides.apply(graph, RingsConfig(notCompositionRoots = listOf("com.app.Gone")))

        assertEquals(listOf(UnhonouredDirective(DirectiveKind.NOT_COMPOSITION_ROOT, "com.app.Gone")), result.unhonoured)
    }

    @Test
    fun `a configured serviceTier pattern marks a matching class as service tier`() {
        val retryKt = ClassName("com.app.RetryKt")

        val result = RingConfigOverrides.applyServiceTier(
            serviceTier = emptySet(),
            classes = setOf(retryKt, renderer),
            config = RingsConfig(serviceTier = listOf("com.app.RetryKt")),
        )

        assertEquals(setOf(retryKt), result.serviceTier)
    }

    @Test
    fun `a notServiceTier pattern removes a class from the detected service tier`() {
        val retryKt = ClassName("com.app.RetryKt")

        val result = RingConfigOverrides.applyServiceTier(
            serviceTier = setOf(retryKt),
            classes = setOf(retryKt, renderer),
            config = RingsConfig(notServiceTier = listOf("com.app.RetryKt")),
        )

        assertEquals(emptySet(), result.serviceTier)
    }

    @Test
    fun `a serviceTier directive that matches no class is reported as unhonoured`() {
        val result = RingConfigOverrides.applyServiceTier(
            serviceTier = emptySet(),
            classes = setOf(renderer),
            config = RingsConfig(serviceTier = listOf("com.app.Gone")),
        )

        assertEquals(listOf(UnhonouredDirective(DirectiveKind.SERVICE_TIER, "com.app.Gone")), result.unhonoured)
    }
}
