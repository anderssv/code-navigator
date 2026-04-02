package no.f12.codenavigator.navigation

import no.f12.codenavigator.navigation.core.ClassName
import no.f12.codenavigator.navigation.callgraph.CallTreeNode
import no.f12.codenavigator.navigation.callgraph.MethodRef
import no.f12.codenavigator.navigation.classinfo.ClassDetail
import no.f12.codenavigator.navigation.context.ContextBuilder
import no.f12.codenavigator.navigation.interfaces.ImplementorInfo
import kotlin.test.Test
import kotlin.test.assertEquals

class ContextBuilderTest {

    @Test
    fun `builds result with class detail`() {
        val classDetail = aClassDetail()

        val result = ContextBuilder.build(
            classDetail = classDetail,
            callers = emptyList(),
            callees = emptyList(),
            implementors = emptyList(),
            implementedInterfaces = emptyList(),
        )

        assertEquals(classDetail, result.classDetail)
    }

    @Test
    fun `builds result with callers`() {
        val callerNode = aCallTreeNode(method = MethodRef(ClassName("com.example.Caller"), "invoke"))

        val result = ContextBuilder.build(
            classDetail = aClassDetail(),
            callers = listOf(callerNode),
            callees = emptyList(),
            implementors = emptyList(),
            implementedInterfaces = emptyList(),
        )

        assertEquals(1, result.callers.size)
        assertEquals("com.example.Caller", result.callers[0].method.className.toString())
    }

    @Test
    fun `builds result with callees`() {
        val calleeNode = aCallTreeNode(method = MethodRef(ClassName("com.example.Dep"), "doWork"))

        val result = ContextBuilder.build(
            classDetail = aClassDetail(),
            callers = emptyList(),
            callees = listOf(calleeNode),
            implementors = emptyList(),
            implementedInterfaces = emptyList(),
        )

        assertEquals(1, result.callees.size)
        assertEquals("com.example.Dep", result.callees[0].method.className.toString())
    }

    @Test
    fun `builds result with interface implementors`() {
        val impls = listOf(
            ImplementorInfo(ClassName("com.example.ImplA"), "ImplA.kt"),
            ImplementorInfo(ClassName("com.example.ImplB"), "ImplB.kt"),
        )

        val result = ContextBuilder.build(
            classDetail = aClassDetail(),
            callers = emptyList(),
            callees = emptyList(),
            implementors = impls,
            implementedInterfaces = emptyList(),
        )

        assertEquals(2, result.implementors.size)
        assertEquals(ClassName("com.example.ImplA"), result.implementors[0].className)
    }

    @Test
    fun `builds result with interfaces the class implements`() {
        val interfaces = listOf(
            ClassName("com.example.Service"),
            ClassName("java.io.Serializable"),
        )

        val result = ContextBuilder.build(
            classDetail = aClassDetail(),
            callers = emptyList(),
            callees = emptyList(),
            implementors = emptyList(),
            implementedInterfaces = interfaces,
        )

        assertEquals(2, result.implementedInterfaces.size)
        assertEquals(ClassName("com.example.Service"), result.implementedInterfaces[0])
    }

    @Test
    fun `builds result with all sections populated`() {
        val result = ContextBuilder.build(
            classDetail = aClassDetail(),
            callers = listOf(aCallTreeNode()),
            callees = listOf(aCallTreeNode()),
            implementors = listOf(ImplementorInfo(ClassName("com.example.Impl"), "Impl.kt")),
            implementedInterfaces = listOf(ClassName("com.example.Iface")),
        )

        assertEquals("com.example.MyService", result.classDetail.className.toString())
        assertEquals(1, result.callers.size)
        assertEquals(1, result.callees.size)
        assertEquals(1, result.implementors.size)
        assertEquals(1, result.implementedInterfaces.size)
    }

    @Test
    fun `builds result with empty callers and callees`() {
        val result = ContextBuilder.build(
            classDetail = aClassDetail(),
            callers = emptyList(),
            callees = emptyList(),
            implementors = emptyList(),
            implementedInterfaces = emptyList(),
        )

        assertEquals(emptyList<CallTreeNode>(), result.callers)
        assertEquals(emptyList<CallTreeNode>(), result.callees)
        assertEquals(emptyList<ImplementorInfo>(), result.implementors)
        assertEquals(emptyList<ClassName>(), result.implementedInterfaces)
    }

    private fun aClassDetail(): ClassDetail = ClassDetail(
        className = ClassName("com.example.MyService"),
        sourceFile = "MyService.kt",
        superClass = null,
        interfaces = emptyList(),
        fields = emptyList(),
        methods = emptyList(),
        annotations = emptyList(),
    )

    private fun aCallTreeNode(
        method: MethodRef = MethodRef(ClassName("com.example.Test"), "test"),
    ): CallTreeNode = CallTreeNode(
        method = method,
        sourceFile = "Test.kt",
        lineNumber = 10,
        children = emptyList(),
    )
}
