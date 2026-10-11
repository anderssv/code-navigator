package no.f12.codenavigator.navigation.declaration

import kotlin.test.Test
import kotlin.test.assertEquals

class DeclarationFilterTest {

    private fun decl(fqn: String, kind: DeclarationKind, sourcePath: String = "com/example/Foo.kt") = DeclarationInfo(
        kind = kind, fqn = fqn, name = fqn.substringAfterLast('.'), signature = fqn, file = "/abs/$sourcePath",
        sourcePath = sourcePath, startLine = 1, startColumn = 1, endLine = 1, endColumn = 1, startOffset = 0, endOffset = 1, nameLine = 1,
    )

    private val decls = listOf(
        decl("com.example.OrderService", DeclarationKind.CLASS),
        decl("com.example.OrderService.place", DeclarationKind.FUNCTION),
        decl("com.example.OrderService.repository", DeclarationKind.PROPERTY),
        decl("com.example.other.PlaceHolder", DeclarationKind.CLASS, "com/example/other/PlaceHolder.kt"),
    )

    @Test
    fun `unqualified pattern matches the declaration name only`() {
        assertEquals(listOf("com.example.OrderService"), DeclarationFilter.filter(decls, "OrderService", null).map { it.fqn })
        assertEquals(
            listOf("com.example.OrderService.place", "com.example.other.PlaceHolder"),
            DeclarationFilter.filter(decls, "place", null).map { it.fqn },
            "case-insensitive substring on the name",
        )
    }

    @Test
    fun `qualified pattern matches within the dotted fqn`() {
        assertEquals(listOf("com.example.OrderService.place"), DeclarationFilter.filter(decls, "OrderService.place", null).map { it.fqn })
        assertEquals(
            listOf("com.example.OrderService.place", "com.example.OrderService.repository"),
            DeclarationFilter.filter(decls, "OrderService\\..*", null).map { it.fqn },
            "members of the class, but not the class itself",
        )
    }

    @Test
    fun `qualified pattern also matches the source path`() {
        assertEquals(listOf("com.example.other.PlaceHolder"), DeclarationFilter.filter(decls, "example/other/.*\\.kt", null).map { it.fqn })
    }

    @Test
    fun `kind restricts by group`() {
        assertEquals(listOf("com.example.OrderService.place"), DeclarationFilter.filter(decls, "OrderService\\..*", DeclarationKindGroup.FUNCTION).map { it.fqn })
        assertEquals(emptyList(), DeclarationFilter.filter(decls, "OrderService", DeclarationKindGroup.PROPERTY))
    }
}
