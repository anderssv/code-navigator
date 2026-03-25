package no.f12.codenavigator.navigation

import kotlin.test.Test
import kotlin.test.assertEquals

class LambdaCollapserTest {

    @Test
    fun `regular class name is unchanged`() {
        assertEquals("com.example.Service", LambdaCollapser.collapse("com.example.Service"))
    }

    @Test
    fun `lambda class Foo dollar 1 collapses to Foo`() {
        assertEquals("com.example.Foo", LambdaCollapser.collapse("com.example.Foo\$1"))
    }

    @Test
    fun `nested lambda collapses past function name to enclosing class`() {
        assertEquals("com.example.Foo", LambdaCollapser.collapse("com.example.Foo\$bar\$1\$2"))
    }

    @Test
    fun `named inner class is unchanged`() {
        assertEquals("com.example.Foo\$Bar", LambdaCollapser.collapse("com.example.Foo\$Bar"))
    }

    @Test
    fun `deep numeric nesting collapses to outermost class`() {
        assertEquals("com.example.Foo", LambdaCollapser.collapse("com.example.Foo\$1\$2\$3"))
    }

    @Test
    fun `lambda under named inner class collapses to named inner`() {
        assertEquals("com.example.Foo\$Bar", LambdaCollapser.collapse("com.example.Foo\$Bar\$1"))
    }

    @Test
    fun `class with digit in named segment is unchanged`() {
        assertEquals("com.example.Foo\$V2", LambdaCollapser.collapse("com.example.Foo\$V2"))
    }

    @Test
    fun `fully qualified lambda collapses to enclosing class`() {
        assertEquals("com.example.Service", LambdaCollapser.collapse("com.example.Service\$handle\$1"))
    }

    @Test
    fun `lambda in function of named inner class collapses to inner class`() {
        assertEquals("com.example.Foo\$Bar", LambdaCollapser.collapse("com.example.Foo\$Bar\$baz\$1"))
    }

    @Test
    fun `collapseComplexity with no lambda classes returns unchanged results`() {
        val input = listOf(
            ClassComplexity(
                className = "com.example.Service",
                sourceFile = "Service.kt",
                fanOut = 2,
                fanIn = 1,
                distinctOutgoingClasses = 2,
                distinctIncomingClasses = 1,
                outgoingByClass = listOf("com.example.Repo" to 1, "com.example.Cache" to 1),
                incomingByClass = listOf("com.example.Controller" to 1),
            ),
        )

        val result = LambdaCollapser.collapseComplexity(input)

        assertEquals(input, result)
    }

    @Test
    fun `collapseComplexity merges lambda callers into enclosing class`() {
        val input = listOf(
            ClassComplexity(
                className = "com.example.Service",
                sourceFile = "Service.kt",
                fanOut = 0,
                fanIn = 3,
                distinctOutgoingClasses = 0,
                distinctIncomingClasses = 3,
                outgoingByClass = emptyList(),
                incomingByClass = listOf(
                    "com.example.Controller" to 1,
                    "com.example.Controller\$handle\$1" to 1,
                    "com.example.Controller\$handle\$2" to 1,
                ),
            ),
        )

        val result = LambdaCollapser.collapseComplexity(input)

        val c = result.first()
        assertEquals(1, c.distinctIncomingClasses)
        assertEquals(listOf("com.example.Controller" to 3), c.incomingByClass)
        assertEquals(3, c.fanIn)
    }

    @Test
    fun `collapseComplexity merges lambda callees into enclosing class`() {
        val input = listOf(
            ClassComplexity(
                className = "com.example.Service",
                sourceFile = "Service.kt",
                fanOut = 2,
                fanIn = 0,
                distinctOutgoingClasses = 2,
                distinctIncomingClasses = 0,
                outgoingByClass = listOf(
                    "com.example.Repo" to 1,
                    "com.example.Repo\$save\$1" to 1,
                ),
                incomingByClass = emptyList(),
            ),
        )

        val result = LambdaCollapser.collapseComplexity(input)

        val c = result.first()
        assertEquals(1, c.distinctOutgoingClasses)
        assertEquals(listOf("com.example.Repo" to 2), c.outgoingByClass)
        assertEquals(2, c.fanOut)
    }
}
