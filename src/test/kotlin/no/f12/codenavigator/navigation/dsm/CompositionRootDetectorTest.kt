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

    @Test
    fun `a framework-invoked controller that reaches an adapter is not a composition root`() {
        // A Spring MVC controller: nothing in the compiled bytecode calls it (the framework invokes
        // it reflectively via annotation-based routing), so it looks exactly like a true composition
        // root by "unreferenced + reaches an adapter" alone. But the controller is itself already an
        // adapter (it directly names a framework type in its own signature, e.g. org.springframework.ui.Model) —
        // a composition root is a plain assembler, never itself framework-driven, so an already-classified
        // adapter must never also become a composition root.
        val controller = ClassName("com.app.web.OwnerController")
        val repository = ClassName("com.app.infra.OwnerRepository")

        val graph = RingGraph(
            classes = setOf(controller, repository),
            ioClasses = setOf(controller, repository),
            dependsOn = mapOf(controller to setOf(repository)),
        )

        val roots = CompositionRootDetector.detect(graph)

        assertEquals(emptySet(), roots, "an already-classified adapter must not also become a composition root")
    }
}
