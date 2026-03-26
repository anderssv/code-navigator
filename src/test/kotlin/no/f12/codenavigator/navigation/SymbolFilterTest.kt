package no.f12.codenavigator.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SymbolFilterTest {

    private val symbols = listOf(
        SymbolInfo(PackageName("com.example.services"), ClassName("com.example.services.UserService"), "findUser", SymbolKind.METHOD, "UserService.kt"),
        SymbolInfo(PackageName("com.example.domain"), ClassName("com.example.domain.UserInfo"), "nationalId", SymbolKind.FIELD, "UserInfo.kt"),
        SymbolInfo(PackageName("com.example.services"), ClassName("com.example.services.ResetService"), "resetPassword", SymbolKind.METHOD, "ResetService.kt"),
    )

    @Test
    fun `matches against symbol name`() {
        val results = SymbolFilter.filter(symbols, "findUser")

        assertEquals(1, results.size)
        assertEquals("findUser", results.first().symbolName)
    }

    @Test
    fun `matches against class name`() {
        val results = SymbolFilter.filter(symbols, "UserService")

        assertEquals(1, results.size)
        assertEquals(ClassName("com.example.services.UserService"), results.first().className)
    }

    @Test
    fun `matches against package name`() {
        val results = SymbolFilter.filter(symbols, "domain")

        assertEquals(1, results.size)
        assertEquals("nationalId", results.first().symbolName)
    }

    @Test
    fun `matches are case insensitive`() {
        val results = SymbolFilter.filter(symbols, "RESETPASSWORD")

        assertEquals(1, results.size)
        assertEquals("resetPassword", results.first().symbolName)
    }

    @Test
    fun `matches using regex pattern`() {
        val results = SymbolFilter.filter(symbols, ".*User.*")

        assertEquals(2, results.size)
    }

    @Test
    fun `returns empty list when no matches`() {
        val results = SymbolFilter.filter(symbols, "nonexistent")

        assertTrue(results.isEmpty())
    }

    @Test
    fun `matches against source file`() {
        val results = SymbolFilter.filter(symbols, "ResetService\\.kt")

        assertEquals(1, results.size)
        assertEquals("resetPassword", results.first().symbolName)
    }
}
