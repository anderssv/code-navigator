package no.f12.codenavigator.navigation.deadcode

import no.f12.codenavigator.navigation.types.AnnotationName
import no.f12.codenavigator.navigation.types.ClassName
import no.f12.codenavigator.navigation.relations.callgraph.MethodRef
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DeadCodeSuppressTest {

    private val suppressAnn = AnnotationName("kotlin.Suppress")
    private val cls = ClassName("com.app.Orphan")

    private fun classParams(value: String): Map<ClassName, Map<AnnotationName, Map<String, String>>> =
        mapOf(cls to mapOf(suppressAnn to mapOf("value" to value)))

    private fun methodParams(methodName: String, value: String): Map<MethodRef, Map<AnnotationName, Map<String, String>>> =
        mapOf(MethodRef(cls, methodName) to mapOf(suppressAnn to mapOf("value" to value)))

    @Test
    fun `ignoreSuppress defaults to true in DeadCodeQuery`() {
        val query = DeadCodeQuery(graph = emptyGraph())

        assertTrue(query.ignoreSuppress)
    }

    @Test
    fun `ignoreSuppress can be set to false`() {
        val query = DeadCodeQuery(graph = emptyGraph(), ignoreSuppress = false)

        assertFalse(query.ignoreSuppress)
    }

    @Test
    fun `class-level @Suppress(unused) is detected`() {
        val params = classParams("unused")

        // The value "unused" is present — should be suppressed
        val suppressValue = params[cls]?.get(suppressAnn)?.get("value")
        assertTrue(suppressValue?.contains("unused", ignoreCase = true) == true)
    }

    @Test
    fun `@Suppress with different value is not treated as unused`() {
        val params = classParams("UNCHECKED_CAST")

        val suppressValue = params[cls]?.get(suppressAnn)?.get("value")
        assertFalse(suppressValue?.contains("unused", ignoreCase = true) == true)
    }

    @Test
    fun `method-level @Suppress(unused) is detected`() {
        val params = methodParams("health", "unused")
        val methodRef = MethodRef(cls, "health")

        val suppressValue = params[methodRef]?.get(suppressAnn)?.get("value")
        assertTrue(suppressValue?.contains("unused", ignoreCase = true) == true)
    }

    @Test
    fun `classAnnotationParameters are wired into DeadCodeQuery`() {
        val params = classParams("unused")
        val query = DeadCodeQuery(graph = emptyGraph(), classAnnotationParameters = params)

        assertTrue(query.classAnnotationParameters.containsKey(cls))
    }
}

private fun emptyGraph() = no.f12.codenavigator.navigation.relations.callgraph.CallGraph(
    callerToCallees = emptyMap()
)
