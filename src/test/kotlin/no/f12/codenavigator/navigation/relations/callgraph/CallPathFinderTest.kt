package no.f12.codenavigator.navigation.relations.callgraph

import no.f12.codenavigator.navigation.annotation.AnnotationExtractor
import no.f12.codenavigator.navigation.relations.implementors.InterfaceRegistry
import no.f12.codenavigator.navigation.types.ClassName
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Runs the finder on bytecode compiled from test-project (com.example.variants.callpaths). Each scenario
 * lives in its own subpackage, so every test only touches the classes it names.
 */
class CallPathFinderTest {

    private companion object {
        val classes = File("test-project/build/classes/kotlin/main")
        val graph: CallGraph by lazy { CallGraphBuilder.build(listOf(classes)).data }
        val implementors: Map<ClassName, Set<ClassName>> by lazy { InterfaceRegistry.build(listOf(classes)).data.implementorMap() }
        val annotations by lazy { CallPathFinder.annotationLookup(AnnotationExtractor.scanAll(listOf(classes))) }
        const val PKG = "com.example.variants.callpaths"
        val AUTHORIZE = Regex("authorize", RegexOption.IGNORE_CASE)
    }

    private fun m(scenario: String, cls: String, method: String) = MethodRef(ClassName("$PKG.$scenario.$cls"), method)

    private fun find(
        entries: List<MethodRef>,
        targets: Set<MethodRef>,
        maxDepth: Int = 8,
        guard: Regex? = null,
        guardDepth: Int = 1,
        filter: ((MethodRef) -> Boolean)? = graph.projectClassFilter(),
        maxPathsPerEntry: Int = 3,
        maxEntries: Int = 50,
    ) = CallPathFinder.find(
        graph, entries, targets, maxDepth,
        guard = guard, guardDepth = guardDepth, filter = filter,
        interfaceImplementors = implementors, annotationsOf = annotations,
        maxPathsPerEntry = maxPathsPerEntry, maxEntries = maxEntries,
    )

    private fun names(path: List<PathStep>) = path.map { "${it.method.className.simpleName()}.${it.method.methodName}" }

    // --- Paths without a guard ---

    @Test
    fun `finds a path from entry to target`() {
        val result = find(listOf(m("direct", "Controller", "delete")), setOf(m("direct", "Repo", "delete")))

        assertEquals(
            listOf("Controller.delete", "Service.delete", "Repo.delete"),
            names(result.reaching.single().paths.single()),
        )
    }

    @Test
    fun `steps carry the source file and first line of each method`() {
        val result = find(listOf(m("direct", "Controller", "delete")), setOf(m("direct", "Repo", "delete")))

        val steps = result.reaching.single().paths.single()
        assertEquals(listOf("Direct.kt", "Direct.kt", "Direct.kt"), steps.map { it.sourceFile })
        assertTrue(steps.all { (it.lineNumber ?: 0) > 0 }, "expected line numbers, got ${steps.map { it.lineNumber }}")
    }

    @Test
    fun `no path when the target is beyond maxdepth`() {
        val result = find(listOf(m("direct", "Controller", "delete")), setOf(m("direct", "Repo", "delete")), maxDepth = 1)

        assertTrue(result.reaching.isEmpty())
    }

    @Test
    fun `maxdepth counts calls along the path`() {
        val entry = listOf(m("shapes", "Step1", "run"))
        val target = setOf(m("shapes", "Repo", "delete"))

        assertEquals(1, find(entry, target, maxDepth = 3).reaching.size)
        assertTrue(find(entry, target, maxDepth = 2).reaching.isEmpty())
    }

    @Test
    fun `shortest path is listed first`() {
        val result = find(listOf(m("direct", "Controller", "deleteTwice")), setOf(m("direct", "Repo", "delete")))

        val paths = result.reaching.single().paths.map { names(it) }
        assertEquals(
            listOf(
                listOf("Controller.deleteTwice", "Repo.delete"),
                listOf("Controller.deleteTwice", "Service.delete", "Repo.delete"),
            ),
            paths,
        )
    }

    @Test
    fun `caps paths per entry and flags that more exist`() {
        val entry = listOf(m("shapes", "FanOut", "go"))
        val target = setOf(m("shapes", "Repo", "delete"))

        val capped = find(entry, target, maxPathsPerEntry = 2)
        val all = find(entry, target, maxPathsPerEntry = 3)

        assertEquals(2, capped.reaching.single().paths.size)
        assertTrue(capped.reaching.single().morePaths)
        assertEquals(3, all.reaching.single().paths.size)
        assertFalse(all.reaching.single().morePaths)
    }

    @Test
    fun `survives cycles`() {
        val result = find(listOf(m("shapes", "Ping", "ping")), setOf(m("shapes", "Repo", "delete")))

        assertEquals(listOf(listOf("Ping.ping", "Repo.delete")), result.reaching.single().paths.map { names(it) })
    }

    @Test
    fun `follows calls made inside a lambda body`() {
        val result = find(listOf(m("shapes", "Scheduler", "schedule")), setOf(m("shapes", "Repo", "delete")))

        val path = names(result.reaching.single().paths.single())
        assertEquals("Scheduler.schedule", path.first())
        assertEquals("Repo.delete", path.last())
        assertTrue(path.any { "lambda" in it }, "expected the lambda body as a step, got $path")
    }

    @Test
    fun `expands interface calls to implementors and records the interface`() {
        val result = find(listOf(m("dispatch", "Controller", "delete")), setOf(m("dispatch", "Repo", "delete")))

        val paths = result.reaching.single().paths
        assertEquals(
            setOf(
                listOf("Controller.delete", "SafeAdapter.delete", "Repo.delete"),
                listOf("Controller.delete", "UnsafeAdapter.delete", "Repo.delete"),
            ),
            paths.map { names(it) }.toSet(),
        )
        assertTrue(paths.all { it[1].via == ClassName("$PKG.dispatch.Port") })
    }

    @Test
    fun `an interface method can itself be the target`() {
        val result = find(listOf(m("dispatch", "Controller", "delete")), setOf(m("dispatch", "Port", "delete")))

        assertEquals(listOf("Controller.delete", "Port.delete"), names(result.reaching.single().paths.single()))
    }

    @Test
    fun `filter hides intermediates but never the target`() {
        val entry = listOf(m("direct", "Controller", "delete"))
        val repo = m("direct", "Repo", "delete")
        val service = m("direct", "Service", "delete")

        assertTrue(find(entry, setOf(repo), filter = { it != service }).reaching.isEmpty())
        assertEquals(1, find(entry, setOf(repo), filter = { it != repo }).reaching.size)
    }

    @Test
    fun `entries that never reach the target are counted but not listed`() {
        val controller = m("direct", "Controller", "delete")

        val result = find(listOf(controller, m("direct", "Health", "check")), setOf(m("direct", "Repo", "delete")))

        assertEquals(2, result.entryCount)
        assertEquals(listOf(controller), result.reaching.map { it.entry })
    }

    @Test
    fun `an entry that is itself a target is skipped`() {
        val repo = m("direct", "Repo", "delete")

        assertTrue(find(listOf(repo), setOf(repo)).reaching.isEmpty())
    }

    @Test
    fun `several targets - a path ends at the first target reached`() {
        val result = find(
            listOf(m("direct", "Controller", "delete")),
            setOf(m("direct", "Service", "delete"), m("direct", "Repo", "delete")),
        )

        assertEquals(listOf(listOf("Controller.delete", "Service.delete")), result.reaching.single().paths.map { names(it) })
    }

    @Test
    fun `caps the number of entries listed`() {
        val entries = listOf("A", "B", "C").map { m("shapes", it, "go") }
        val target = setOf(m("shapes", "Repo", "delete"))

        val capped = find(entries, target, maxEntries = 2)

        assertEquals(2, capped.reaching.size)
        assertTrue(capped.truncated)
        assertFalse(find(entries, target).truncated)
    }

    // --- Guard ---

    @Test
    fun `a method calling the guard directly guards the path`() {
        val controller = m("guarded", "Controller", "delete")

        val result = find(listOf(controller), setOf(m("guarded", "Repo", "delete")), guard = AUTHORIZE)

        assertTrue(result.reaching.isEmpty())
        assertEquals(listOf(controller), result.guardedEntries)
    }

    @Test
    fun `reports only the unguarded paths`() {
        val result = find(listOf(m("guarded", "Controller", "deleteOrPurge")), setOf(m("guarded", "Repo", "delete")), guard = AUTHORIZE)

        assertEquals(
            listOf("Controller.deleteOrPurge", "Purger.purge", "Repo.delete"),
            names(result.reaching.single().paths.single()),
        )
        assertTrue(result.guardedEntries.isEmpty())
    }

    @Test
    fun `guard reached through a helper counts only within guard-depth`() {
        val controller = m("helper", "Controller", "delete")
        val target = setOf(m("helper", "Repo", "delete"))

        val direct = find(listOf(controller), target, guard = AUTHORIZE, guardDepth = 1)
        val deeper = find(listOf(controller), target, guard = AUTHORIZE, guardDepth = 2)

        assertEquals(1, direct.reaching.size)
        assertTrue(deeper.reaching.isEmpty())
        assertEquals(listOf(controller), deeper.guardedEntries)
    }

    @Test
    fun `guard-depth 0 only counts methods that match the guard themselves`() {
        val result = find(listOf(m("helper", "Controller", "deleteDirect")), setOf(m("helper", "Repo", "delete")), guard = AUTHORIZE, guardDepth = 0)

        assertEquals(1, result.reaching.size)
    }

    @Test
    fun `guard regex matches the qualified name`() {
        val entry = listOf(m("helper", "Controller", "deleteDirect"))
        val target = setOf(m("helper", "Repo", "delete"))

        assertTrue(find(entry, target, guard = Regex("callpaths\\.Auth\\.authorize")).reaching.isEmpty())
        assertEquals(1, find(entry, target, guard = Regex("otherpackage\\.Auth\\.authorize")).reaching.size)
    }

    @Test
    fun `guard on the target itself guards every path`() {
        val controller = m("guarded", "Controller", "delete")

        val result = find(listOf(controller), setOf(m("guarded", "Service", "delete")), guard = AUTHORIZE)

        assertTrue(result.reaching.isEmpty())
        assertEquals(listOf(controller), result.guardedEntries)
    }

    @Test
    fun `one guarded and one unguarded implementor - only the unguarded one is reported`() {
        val result = find(listOf(m("dispatch", "Controller", "delete")), setOf(m("dispatch", "Repo", "delete")), guard = AUTHORIZE)

        assertEquals(
            listOf("Controller.delete", "UnsafeAdapter.delete", "Repo.delete"),
            names(result.reaching.single().paths.single()),
        )
    }

    @Test
    fun `guard method hidden by the filter still guards`() {
        val controller = m("guarded", "Controller", "delete")
        val authorize = MethodRef(ClassName("$PKG.Auth"), "authorize")

        val result = find(
            listOf(controller), setOf(m("guarded", "Repo", "delete")), guard = AUTHORIZE,
            filter = { graph.projectClassFilter()(it) && it != authorize },
        )

        assertTrue(result.reaching.isEmpty())
        assertEquals(listOf(controller), result.guardedEntries)
    }

    @Test
    fun `an annotation matching the guard guards the method`() {
        val admin = m("annotated", "AdminController", "delete")
        val public = m("annotated", "PublicController", "delete")

        val result = find(listOf(admin, public), setOf(m("annotated", "Repo", "delete")), guard = Regex("RequiresRole"))

        assertEquals(listOf(public), result.reaching.map { it.entry })
        assertEquals(listOf(admin), result.guardedEntries)
    }

    @Test
    fun `mixed entries - guarded, unguarded and unrelated are kept apart`() {
        val guarded = m("guarded", "Controller", "delete")
        val open = m("guarded", "Purger", "purge")
        val unrelated = m("direct", "Health", "check")

        val result = find(listOf(guarded, open, unrelated), setOf(m("guarded", "Repo", "delete")), guard = AUTHORIZE)

        assertEquals(3, result.entryCount)
        assertEquals(listOf(open), result.reaching.map { it.entry })
        assertEquals(listOf(guarded), result.guardedEntries)
    }

    @Test
    fun `an entry beyond maxdepth is neither reaching nor guarded`() {
        val result = find(listOf(m("guarded", "Controller", "delete")), setOf(m("guarded", "Repo", "delete")), maxDepth = 1, guard = AUTHORIZE)

        assertTrue(result.reaching.isEmpty())
        assertTrue(result.guardedEntries.isEmpty())
    }

    // --- Entry selection ---

    private val methodAnnotations by lazy { AnnotationExtractor.scanAll(listOf(classes)).methodAnnotations }

    private fun select(pattern: String? = null, annotation: Regex? = null, filter: ((MethodRef) -> Boolean)? = null) =
        CallPathFinder.selectEntries(graph, pattern, annotation, methodAnnotations, filter)

    @Test
    fun `entries by method annotation`() {
        val entries = select(annotation = Regex("callpaths\\.Post$"))

        assertEquals(
            setOf(m("annotated", "AdminController", "delete"), m("annotated", "PublicController", "delete")),
            entries.toSet(),
        )
    }

    @Test
    fun `entries by pattern`() {
        val entries = select(pattern = "callpaths\\.annotated\\.PublicController\\.")

        assertEquals(
            setOf(m("annotated", "PublicController", "delete"), m("annotated", "PublicController", "internalDelete")),
            entries.toSet(),
        )
    }

    @Test
    fun `pattern entries skip constructors, static initializers and field reads`() {
        val entries = select(pattern = "callpaths\\.guarded\\.Controller\\.")

        assertEquals(
            setOf(m("guarded", "Controller", "delete"), m("guarded", "Controller", "deleteOrPurge")),
            entries.toSet(),
        )
    }

    @Test
    fun `pattern entries skip methods of compiler-made anonymous classes`() {
        val entries = select(pattern = "callpaths.suspending.Routes.handle")

        assertEquals(listOf(m("suspending", "Routes", "handle")), entries)
    }

    @Test
    fun `pattern and annotation together select the methods matching both`() {
        val entries = select(pattern = "PublicController", annotation = Regex("callpaths\\.Post$"))

        assertEquals(listOf(m("annotated", "PublicController", "delete")), entries)
    }

    @Test
    fun `filter removes entries`() {
        val admin = m("annotated", "AdminController", "delete")

        val entries = select(annotation = Regex("callpaths\\.Post$"), filter = { it != admin })

        assertEquals(listOf(m("annotated", "PublicController", "delete")), entries)
    }

    @Test
    fun `no pattern and no annotation is rejected`() {
        val error = kotlin.runCatching { select() }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
    }
}
