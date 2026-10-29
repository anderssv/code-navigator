package no.f12.codenavigator.navigation.refactor

import kotlin.test.Test
import kotlin.test.assertEquals

class ImportOrderingTest {

    private fun file(vararg imports: String) = "package p\n\n" + imports.joinToString("\n") { "import $it" } + "\n\nclass X\n"

    @Test
    fun `an import the move changed is moved to its sorted place when the file was sorted`() {
        val before = file("a.api.Foo", "a.endpoint.Bar", "a.endpoint.Mapper", "org.junit.Test")
        val after = file("a.api.Foo", "a.endpoint.Bar", "a.domain.Mapper", "org.junit.Test")

        assertEquals(file("a.api.Foo", "a.domain.Mapper", "a.endpoint.Bar", "org.junit.Test"), ImportOrdering.placeNewImports(before, after))
    }

    @Test
    fun `an added import is not left after org imports`() {
        val before = file("a.endpoint.Bar", "org.junit.Test")
        val after = file("a.endpoint.Bar", "org.junit.Test", "a.domain.Mapper")

        assertEquals(file("a.domain.Mapper", "a.endpoint.Bar", "org.junit.Test"), ImportOrdering.placeNewImports(before, after))
    }

    @Test
    fun `ktlint layout puts java, javax and kotlin last and aliases at the end`() {
        val before = file("b.Two", "java.util.UUID", "kotlin.test.Test", "c.Three as T")
        val after = file("b.Two", "java.util.UUID", "kotlin.test.Test", "c.Three as T", "a.One")

        assertEquals(file("a.One", "b.Two", "java.util.UUID", "kotlin.test.Test", "c.Three as T"), ImportOrdering.placeNewImports(before, after))
    }

    @Test
    fun `a file whose imports were not sorted keeps the order the move produced`() {
        val before = file("z.Last", "a.First")
        val after = file("z.Last", "a.First", "m.Middle")

        assertEquals(after, ImportOrdering.placeNewImports(before, after))
    }
}
