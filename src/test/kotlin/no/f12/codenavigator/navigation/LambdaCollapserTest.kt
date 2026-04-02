package no.f12.codenavigator.navigation

import no.f12.codenavigator.navigation.core.ClassName
import no.f12.codenavigator.navigation.core.LambdaCollapser
import no.f12.codenavigator.navigation.complexity.ClassComplexity
import kotlin.test.Test
import kotlin.test.assertEquals

class LambdaCollapserTest {

    @Test
    fun `regular class name is unchanged`() {
        assertEquals("com.example.Service", LambdaCollapser.collapse(ClassName("com.example.Service")).value)
    }

    @Test
    fun `lambda class Foo dollar 1 collapses to Foo`() {
        assertEquals("com.example.Foo", LambdaCollapser.collapse(ClassName("com.example.Foo\$1")).value)
    }

    @Test
    fun `nested lambda collapses past function name to enclosing class`() {
        assertEquals("com.example.Foo", LambdaCollapser.collapse(ClassName("com.example.Foo\$bar\$1\$2")).value)
    }

    @Test
    fun `named inner class is unchanged`() {
        assertEquals("com.example.Foo\$Bar", LambdaCollapser.collapse(ClassName("com.example.Foo\$Bar")).value)
    }

    @Test
    fun `deep numeric nesting collapses to outermost class`() {
        assertEquals("com.example.Foo", LambdaCollapser.collapse(ClassName("com.example.Foo\$1\$2\$3")).value)
    }

    @Test
    fun `lambda under named inner class collapses to named inner`() {
        assertEquals("com.example.Foo\$Bar", LambdaCollapser.collapse(ClassName("com.example.Foo\$Bar\$1")).value)
    }

    @Test
    fun `class with digit in named segment is unchanged`() {
        assertEquals("com.example.Foo\$V2", LambdaCollapser.collapse(ClassName("com.example.Foo\$V2")).value)
    }

    @Test
    fun `fully qualified lambda collapses to enclosing class`() {
        assertEquals("com.example.Service", LambdaCollapser.collapse(ClassName("com.example.Service\$handle\$1")).value)
    }

    @Test
    fun `lambda in function of named inner class collapses to inner class`() {
        assertEquals("com.example.Foo\$Bar", LambdaCollapser.collapse(ClassName("com.example.Foo\$Bar\$baz\$1")).value)
    }

    @Test
    fun `collapseComplexity with no lambda classes returns unchanged results`() {
        val input = listOf(
            ClassComplexity(
                className = ClassName("com.example.Service"),
                sourceFile = "Service.kt",
                fanOut = 2,
                fanIn = 1,
                distinctOutgoingClasses = 2,
                distinctIncomingClasses = 1,
                outgoingByClass = listOf(ClassName("com.example.Repo") to 1, ClassName("com.example.Cache") to 1),
                incomingByClass = listOf(ClassName("com.example.Controller") to 1),
            ),
        )

        val result = LambdaCollapser.collapseComplexity(input)

        assertEquals(input, result)
    }

    @Test
    fun `collapseComplexity merges lambda callers into enclosing class`() {
        val input = listOf(
            ClassComplexity(
                className = ClassName("com.example.Service"),
                sourceFile = "Service.kt",
                fanOut = 0,
                fanIn = 3,
                distinctOutgoingClasses = 0,
                distinctIncomingClasses = 3,
                outgoingByClass = emptyList(),
                incomingByClass = listOf(
                    ClassName("com.example.Controller") to 1,
                    ClassName("com.example.Controller\$handle\$1") to 1,
                    ClassName("com.example.Controller\$handle\$2") to 1,
                ),
            ),
        )

        val result = LambdaCollapser.collapseComplexity(input)

        val c = result.first()
        assertEquals(1, c.distinctIncomingClasses)
        assertEquals(listOf(ClassName("com.example.Controller") to 3), c.incomingByClass)
        assertEquals(3, c.fanIn)
    }

    @Test
    fun `collapseComplexity merges lambda callees into enclosing class`() {
        val input = listOf(
            ClassComplexity(
                className = ClassName("com.example.Service"),
                sourceFile = "Service.kt",
                fanOut = 2,
                fanIn = 0,
                distinctOutgoingClasses = 2,
                distinctIncomingClasses = 0,
                outgoingByClass = listOf(
                    ClassName("com.example.Repo") to 1,
                    ClassName("com.example.Repo\$save\$1") to 1,
                ),
                incomingByClass = emptyList(),
            ),
        )

        val result = LambdaCollapser.collapseComplexity(input)

        val c = result.first()
        assertEquals(1, c.distinctOutgoingClasses)
        assertEquals(listOf(ClassName("com.example.Repo") to 2), c.outgoingByClass)
        assertEquals(2, c.fanOut)
    }
}
