package no.f12.codenavigator.navigation.dsm

import no.f12.codenavigator.navigation.types.ClassName
import kotlin.test.Test
import kotlin.test.assertEquals

class CompositionRootDetectorTest {

    @Test
    fun `a class that wires adapters and is referenced by nobody is a composition root`() {
        val root = ClassName("com.app.ApplicationKt")
        val impl = ClassName("com.app.infra.PollsRepositoryImpl")
        val service = ClassName("com.app.polls.PollService")

        val graph = RingGraph(
            classes = setOf(root, impl, service),
            ioClasses = setOf(impl),
            dependsOn = mapOf(
                root to setOf(impl, service),
                service to setOf(impl),
            ),
        )

        val roots = CompositionRootDetector.detect(graph)

        assertEquals(setOf(root), roots)
    }

    @Test
    fun `a class that something else references is not a composition root`() {
        val candidate = ClassName("com.app.Wiring")
        val impl = ClassName("com.app.infra.PollsRepositoryImpl")
        val caller = ClassName("com.app.polls.PollService")

        val graph = RingGraph(
            classes = setOf(candidate, impl, caller),
            ioClasses = setOf(impl),
            dependsOn = mapOf(
                candidate to setOf(impl),
                caller to setOf(candidate),
            ),
        )

        val roots = CompositionRootDetector.detect(graph)

        assertEquals(emptySet(), roots)
    }

    @Test
    fun `a class that wires no adapter is not a composition root`() {
        val entryPoint = ClassName("com.app.PureEntryPointKt")
        val service = ClassName("com.app.polls.PollService")

        val graph = RingGraph(
            classes = setOf(entryPoint, service),
            ioClasses = emptySet(),
            dependsOn = mapOf(entryPoint to setOf(service)),
        )

        val roots = CompositionRootDetector.detect(graph)

        assertEquals(emptySet(), roots)
    }

    @Test
    fun `a codebase with no classes has no composition roots`() {
        val roots = CompositionRootDetector.detect(RingGraph(classes = emptySet()))

        assertEquals(emptySet(), roots)
    }
}
