package no.f12.codenavigator.navigation

import no.f12.codenavigator.navigation.symbol.SymbolFilter
import no.f12.codenavigator.navigation.symbol.SymbolInfo
import no.f12.codenavigator.navigation.symbol.SymbolKind
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
    fun `matches against package name with qualified pattern`() {
        val results = SymbolFilter.filter(symbols, "example.domain")

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

    @Test
    fun `simple pattern does not match package substrings`() {
        val symbolsWithPackageMatch = listOf(
            SymbolInfo(PackageName("com.example.selfservice"), ClassName("com.example.selfservice.PaymentHandler"), "processPayment", SymbolKind.METHOD, "PaymentHandler.kt"),
            SymbolInfo(PackageName("com.example.api"), ClassName("com.example.api.UserService"), "findUser", SymbolKind.METHOD, "UserService.kt"),
        )

        val results = SymbolFilter.filter(symbolsWithPackageMatch, "Service")

        assertEquals(1, results.size)
        assertEquals("UserService", results.first().className.simpleName())
    }

    @Test
    fun `simple pattern does not match FQN package segment`() {
        val symbolsWithFqnMatch = listOf(
            SymbolInfo(PackageName("com.example.selfservice"), ClassName("com.example.selfservice.Handler"), "handle", SymbolKind.METHOD, "Handler.kt"),
        )

        val results = SymbolFilter.filter(symbolsWithFqnMatch, "service")

        assertTrue(results.isEmpty(), "Should not match 'selfservice' in FQN package segment")
    }

    @Test
    fun `qualified pattern matches against full FQN`() {
        val results = SymbolFilter.filter(symbols, "services.UserService")

        assertEquals(1, results.size)
        assertEquals("findUser", results.first().symbolName)
    }
}
