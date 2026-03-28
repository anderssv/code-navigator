package no.f12.codenavigator.navigation

import no.f12.codenavigator.navigation.interfaces.ImplementorInfo
import no.f12.codenavigator.navigation.hierarchy.SupertypeInfo
import no.f12.codenavigator.navigation.hierarchy.SupertypeKind
import no.f12.codenavigator.navigation.hierarchy.TypeHierarchyFormatter
import no.f12.codenavigator.navigation.hierarchy.TypeHierarchyResult
import kotlin.test.Test
import kotlin.test.assertEquals

class TypeHierarchyFormatterTest {

    @Test
    fun `formats class with no supertypes and no implementors`() {
        val result = TypeHierarchyResult(
            className = ClassName("com.example.Simple"),
            sourceFile = "Simple.kt",
            supertypes = emptyList(),
            implementors = emptyList(),
        )

        val output = TypeHierarchyFormatter.format(listOf(result))

        assertEquals("=== com.example.Simple (Simple.kt) ===", output)
    }

    @Test
    fun `formats class with direct superclass`() {
        val result = TypeHierarchyResult(
            className = ClassName("com.example.Child"),
            sourceFile = "Child.kt",
            supertypes = listOf(
                SupertypeInfo(ClassName("com.example.Base"), SupertypeKind.CLASS, emptyList()),
            ),
            implementors = emptyList(),
        )

        val output = TypeHierarchyFormatter.format(listOf(result))

        val expected = """
            |=== com.example.Child (Child.kt) ===
            |Supertypes:
            |  extends com.example.Base
        """.trimMargin()
        assertEquals(expected, output)
    }

    @Test
    fun `formats class with direct interface`() {
        val result = TypeHierarchyResult(
            className = ClassName("com.example.MyClass"),
            sourceFile = "MyClass.kt",
            supertypes = listOf(
                SupertypeInfo(ClassName("com.example.Readable"), SupertypeKind.INTERFACE, emptyList()),
            ),
            implementors = emptyList(),
        )

        val output = TypeHierarchyFormatter.format(listOf(result))

        val expected = """
            |=== com.example.MyClass (MyClass.kt) ===
            |Supertypes:
            |  implements com.example.Readable
        """.trimMargin()
        assertEquals(expected, output)
    }

    @Test
    fun `formats recursive superclass chain with indentation`() {
        val result = TypeHierarchyResult(
            className = ClassName("com.example.Child"),
            sourceFile = "Child.kt",
            supertypes = listOf(
                SupertypeInfo(
                    ClassName("com.example.Parent"), SupertypeKind.CLASS, listOf(
                        SupertypeInfo(ClassName("com.example.Grandparent"), SupertypeKind.CLASS, emptyList()),
                    ),
                ),
            ),
            implementors = emptyList(),
        )

        val output = TypeHierarchyFormatter.format(listOf(result))

        val expected = """
            |=== com.example.Child (Child.kt) ===
            |Supertypes:
            |  extends com.example.Parent
            |    extends com.example.Grandparent
        """.trimMargin()
        assertEquals(expected, output)
    }

    @Test
    fun `formats class with implementors`() {
        val result = TypeHierarchyResult(
            className = ClassName("com.example.Repository"),
            sourceFile = "Repository.kt",
            supertypes = emptyList(),
            implementors = listOf(
                ImplementorInfo(ClassName("com.example.UserRepo"), "UserRepo.kt"),
                ImplementorInfo(ClassName("com.example.OrderRepo"), "OrderRepo.kt"),
            ),
        )

        val output = TypeHierarchyFormatter.format(listOf(result))

        val expected = """
            |=== com.example.Repository (Repository.kt) ===
            |Implementors:
            |  com.example.UserRepo (UserRepo.kt)
            |  com.example.OrderRepo (OrderRepo.kt)
        """.trimMargin()
        assertEquals(expected, output)
    }

    @Test
    fun `formats multiple results separated by blank line`() {
        val results = listOf(
            TypeHierarchyResult(
                className = ClassName("com.example.A"),
                sourceFile = "A.kt",
                supertypes = emptyList(),
                implementors = emptyList(),
            ),
            TypeHierarchyResult(
                className = ClassName("com.example.B"),
                sourceFile = "B.kt",
                supertypes = emptyList(),
                implementors = emptyList(),
            ),
        )

        val output = TypeHierarchyFormatter.format(results)

        val expected = """
            |=== com.example.A (A.kt) ===
            |
            |=== com.example.B (B.kt) ===
        """.trimMargin()
        assertEquals(expected, output)
    }

    @Test
    fun `formats class with both supertypes and implementors`() {
        val result = TypeHierarchyResult(
            className = ClassName("com.example.BaseRepo"),
            sourceFile = "BaseRepo.kt",
            supertypes = listOf(
                SupertypeInfo(ClassName("com.example.Repository"), SupertypeKind.INTERFACE, emptyList()),
            ),
            implementors = listOf(
                ImplementorInfo(ClassName("com.example.UserRepo"), "UserRepo.kt"),
            ),
        )

        val output = TypeHierarchyFormatter.format(listOf(result))

        val expected = """
            |=== com.example.BaseRepo (BaseRepo.kt) ===
            |Supertypes:
            |  implements com.example.Repository
            |Implementors:
            |  com.example.UserRepo (UserRepo.kt)
        """.trimMargin()
        assertEquals(expected, output)
    }
}
