package no.f12.codenavigator.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SymbolTableFormatterTest {

    @Test
    fun `formats symbols as an aligned table`() {
        val symbols = listOf(
            SymbolInfo(PackageName("com.example"), ClassName("com.example.UserService"), "findUser", SymbolKind.METHOD, "UserService.kt"),
            SymbolInfo(PackageName("com.example"), ClassName("com.example.UserInfo"), "name", SymbolKind.FIELD, "UserInfo.kt"),
        )

        val result = SymbolTableFormatter.format(symbols)

        assertTrue(result.contains("Package"))
        assertTrue(result.contains("Class"))
        assertTrue(result.contains("Symbol"))
        assertTrue(result.contains("Kind"))
        assertTrue(result.contains("Source File"))
        assertTrue(result.contains("findUser"))
        assertTrue(result.contains("name"))
        assertTrue(result.contains("2 symbols found."))
    }

    @Test
    fun `returns message for empty list`() {
        val result = SymbolTableFormatter.format(emptyList())

        assertEquals("No symbols found.", result)
    }

    @Test
    fun `columns are properly aligned`() {
        val symbols = listOf(
            SymbolInfo(PackageName("a"), ClassName("a.B"), "c", SymbolKind.METHOD, "B.kt"),
            SymbolInfo(PackageName("aa"), ClassName("aa.BB"), "cc", SymbolKind.FIELD, "BB.kt"),
        )

        val result = SymbolTableFormatter.format(symbols)
        val lines = result.lines()

        val headerPipes = lines[0].indices.filter { lines[0][it] == '|' }
        val dataRowPipes = lines[2].indices.filter { lines[2][it] == '|' }
        assertEquals(headerPipes, dataRowPipes)
    }

    @Test
    fun `shows count matching number of symbols`() {
        val symbols = listOf(
            SymbolInfo(PackageName("com.example"), ClassName("com.example.Foo"), "bar", SymbolKind.METHOD, "Foo.kt"),
        )

        val result = SymbolTableFormatter.format(symbols)

        assertTrue(result.contains("1 symbols found."))
    }
}
