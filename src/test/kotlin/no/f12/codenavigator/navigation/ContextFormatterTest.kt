package no.f12.codenavigator.navigation

import no.f12.codenavigator.navigation.core.ClassName
import no.f12.codenavigator.navigation.callgraph.CallTreeNode
import no.f12.codenavigator.navigation.callgraph.MethodRef
import no.f12.codenavigator.navigation.context.ContextFormatter
import no.f12.codenavigator.navigation.fixtures.aContextResult
import no.f12.codenavigator.navigation.interfaces.ImplementorInfo
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ContextFormatterTest {

    @Test
    fun `formats class detail section`() {
        val result = aContextResult()

        val output = ContextFormatter.format(result)

        assertContains(output, "com.example.MyService")
        assertContains(output, "MyService.kt")
    }

    @Test
    fun `formats callers section with tree`() {
        val callerNode = CallTreeNode(
            method = MethodRef(ClassName("com.example.Caller"), "invoke"),
            sourceFile = "Caller.kt",
            lineNumber = 42,
            children = emptyList(),
        )
        val callerRoot = CallTreeNode(
            method = MethodRef(ClassName("com.example.MyService"), "doWork"),
            sourceFile = "MyService.kt",
            lineNumber = 10,
            children = listOf(callerNode),
        )
        val result = aContextResult(callers = listOf(callerRoot))

        val output = ContextFormatter.format(result)

        assertContains(output, "Callers")
        assertContains(output, "com.example.Caller.invoke")
    }

    @Test
    fun `formats callees section with tree`() {
        val calleeNode = CallTreeNode(
            method = MethodRef(ClassName("com.example.Dep"), "save"),
            sourceFile = "Dep.kt",
            lineNumber = 20,
            children = emptyList(),
        )
        val calleeRoot = CallTreeNode(
            method = MethodRef(ClassName("com.example.MyService"), "doWork"),
            sourceFile = "MyService.kt",
            lineNumber = 10,
            children = listOf(calleeNode),
        )
        val result = aContextResult(callees = listOf(calleeRoot))

        val output = ContextFormatter.format(result)

        assertContains(output, "Callees")
        assertContains(output, "com.example.Dep.save")
    }

    @Test
    fun `omits callers section when empty`() {
        val result = aContextResult(callers = emptyList())

        val output = ContextFormatter.format(result)

        assertFalse(output.contains("Callers"), "Should not show callers section when empty")
    }

    @Test
    fun `omits callees section when empty`() {
        val result = aContextResult(callees = emptyList())

        val output = ContextFormatter.format(result)

        assertFalse(output.contains("Callees"), "Should not show callees section when empty")
    }

    @Test
    fun `formats implementors section`() {
        val result = aContextResult(
            implementors = listOf(
                ImplementorInfo(ClassName("com.example.ImplA"), "ImplA.kt"),
                ImplementorInfo(ClassName("com.example.ImplB"), "ImplB.kt"),
            ),
        )

        val output = ContextFormatter.format(result)

        assertContains(output, "Implementors")
        assertContains(output, "com.example.ImplA")
        assertContains(output, "com.example.ImplB")
    }

    @Test
    fun `omits implementors section when empty`() {
        val result = aContextResult(implementors = emptyList())

        val output = ContextFormatter.format(result)

        assertFalse(output.contains("Implementors"), "Should not show implementors section when empty")
    }

    @Test
    fun `formats implemented interfaces section`() {
        val result = aContextResult(
            implementedInterfaces = listOf(
                ClassName("com.example.Service"),
                ClassName("java.io.Serializable"),
            ),
        )

        val output = ContextFormatter.format(result)

        assertContains(output, "Implements")
        assertContains(output, "com.example.Service")
        assertContains(output, "java.io.Serializable")
    }

    @Test
    fun `omits implemented interfaces section when empty`() {
        val result = aContextResult(implementedInterfaces = emptyList())

        val output = ContextFormatter.format(result)

        assertFalse(output.contains("Implements"), "Should not show implements section when empty")
    }

    @Test
    fun `formats all sections together`() {
        val callerRoot = CallTreeNode(
            method = MethodRef(ClassName("com.example.MyService"), "doWork"),
            sourceFile = "MyService.kt",
            lineNumber = 10,
            children = listOf(
                CallTreeNode(
                    method = MethodRef(ClassName("com.example.Caller"), "run"),
                    sourceFile = "Caller.kt",
                    lineNumber = 5,
                    children = emptyList(),
                ),
            ),
        )
        val calleeRoot = CallTreeNode(
            method = MethodRef(ClassName("com.example.MyService"), "doWork"),
            sourceFile = "MyService.kt",
            lineNumber = 10,
            children = listOf(
                CallTreeNode(
                    method = MethodRef(ClassName("com.example.Repo"), "save"),
                    sourceFile = "Repo.kt",
                    lineNumber = 30,
                    children = emptyList(),
                ),
            ),
        )
        val result = aContextResult(
            callers = listOf(callerRoot),
            callees = listOf(calleeRoot),
            implementors = listOf(ImplementorInfo(ClassName("com.example.Impl"), "Impl.kt")),
            implementedInterfaces = listOf(ClassName("com.example.Iface")),
        )

        val output = ContextFormatter.format(result)

        assertContains(output, "com.example.MyService")
        assertContains(output, "Callers")
        assertContains(output, "Callees")
        assertContains(output, "Implementors")
        assertContains(output, "Implements")
    }

}
