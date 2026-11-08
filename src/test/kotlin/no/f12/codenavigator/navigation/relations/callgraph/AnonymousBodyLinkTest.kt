package no.f12.codenavigator.navigation.relations.callgraph

import no.f12.codenavigator.navigation.types.ClassName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A lambda or object expression compiled to its own class is only constructed by the method that writes it;
 * nothing in bytecode calls its body. The call graph links the construction to the body.
 */
class AnonymousBodyLinkTest {

    private fun ref(cls: String, method: String) = MethodRef(ClassName("p.$cls"), method)

    private val outer = ref("Svc", "removeAll")
    private val lambdaClass = "Svc\$removeAll\$2"
    private val lambdaInit = ref(lambdaClass, "<init>")
    private val lambdaBody = ref(lambdaClass, "invokeSuspend")
    private val delete = ref("Repo", "delete")

    private fun graph(edges: Map<MethodRef, Set<MethodRef>>, declared: Map<String, Set<String>>) =
        CallGraph(edges, declaredMethods = declared.mapKeys { ClassName("p.${it.key}") })

    @Test
    fun `constructing a suspend lambda calls its body`() {
        val g = graph(
            mapOf(outer to setOf(lambdaInit), lambdaBody to setOf(delete)),
            mapOf(lambdaClass to setOf("<init>", "invokeSuspend", "create", "invoke")),
        )

        val callees = g.calleesOf(outer.className, outer.methodName)
        assertEquals(setOf(lambdaInit, lambdaBody), callees, "only invokeSuspend: invoke and create are coroutine plumbing")
        assertTrue(outer in g.callersOf(lambdaBody.className, lambdaBody.methodName))
    }

    @Test
    fun `a plain lambda class links its invoke`() {
        val g = graph(mapOf(outer to setOf(lambdaInit)), mapOf(lambdaClass to setOf("<init>", "invoke")))

        assertTrue(ref(lambdaClass, "invoke") in g.calleesOf(outer.className, outer.methodName))
    }

    @Test
    fun `an object expression's methods are linked too`() {
        val objectClass = "Svc\$start\$1"
        val start = ref("Svc", "start")
        val g = graph(
            mapOf(start to setOf(ref(objectClass, "<init>"))),
            mapOf(objectClass to setOf("<init>", "run")),
        )

        assertTrue(ref(objectClass, "run") in g.calleesOf(start.className, start.methodName))
    }

    @Test
    fun `a suspend function's continuation is not linked - its body calls the function back`() {
        val remove = ref("Svc", "remove")
        val continuation = "Svc\$remove\$1"
        val g = graph(
            mapOf(remove to setOf(ref(continuation, "<init>")), ref(continuation, "invokeSuspend") to setOf(remove)),
            mapOf(continuation to setOf("<init>", "invokeSuspend")),
        )

        assertEquals(setOf(ref(continuation, "<init>")), g.calleesOf(remove.className, remove.methodName))
    }

    @Test
    fun `named nested classes are not linked`() {
        val build = ref("Svc", "build")
        val g = graph(mapOf(build to setOf(ref("Svc\$Builder", "<init>"))), mapOf("Svc\$Builder" to setOf("<init>", "build")))

        assertEquals(setOf(ref("Svc\$Builder", "<init>")), g.calleesOf(build.className, build.methodName))
    }

    @Test
    fun `the linked edges survive the cache`() {
        val g = graph(
            mapOf(outer to setOf(lambdaInit), lambdaBody to setOf(delete)),
            mapOf(lambdaClass to setOf("<init>", "invokeSuspend")),
        )
        val file = kotlin.io.path.createTempFile("call-graph", ".cache").toFile()

        CallGraphCache.write(file, g)

        assertTrue(lambdaBody in CallGraphCache.read(file).calleesOf(outer.className, outer.methodName))
    }
}
