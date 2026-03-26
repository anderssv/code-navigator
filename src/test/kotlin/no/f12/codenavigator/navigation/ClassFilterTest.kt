package no.f12.codenavigator.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ClassFilterTest {

    private val classes = listOf(
        ClassInfo(ClassName("com.example.services.UserService"), "UserService.kt", "com/example/services/UserService.kt", true),
        ClassInfo(ClassName("com.example.services.OrderService"), "OrderService.kt", "com/example/services/OrderService.kt", true),
        ClassInfo(ClassName("com.example.domain.User"), "User.kt", "com/example/domain/User.kt", true),
        ClassInfo(ClassName("com.example.domain.Order"), "Order.kt", "com/example/domain/Order.kt", true),
        ClassInfo(ClassName("com.example.config.AppConfig"), "AppConfig.kt", "com/example/config/AppConfig.kt", true),
    )

    @Test
    fun `matches classes by simple substring regex`() {
        val results = ClassFilter.filter(classes, "Service")

        assertEquals(2, results.size)
        assertTrue(results.all { it.className.value.contains("Service") })
    }

    @Test
    fun `matches classes by full qualified name regex`() {
        val results = ClassFilter.filter(classes, "com\\.example\\.domain\\.User")

        assertEquals(1, results.size)
        assertEquals("com.example.domain.User", results.single().className.value)
    }

    @Test
    fun `returns empty list when no classes match`() {
        val results = ClassFilter.filter(classes, "NonExistent")

        assertTrue(results.isEmpty())
    }

    @Test
    fun `matching is case-insensitive`() {
        val results = ClassFilter.filter(classes, "userservice")

        assertEquals(1, results.size)
        assertEquals("com.example.services.UserService", results.single().className.value)
    }

    @Test
    fun `matches with wildcard regex patterns`() {
        val results = ClassFilter.filter(classes, ".*Order.*")

        assertEquals(2, results.size)
        val classNames = results.map { it.className.value }.toSet()
        assertTrue("com.example.services.OrderService" in classNames)
        assertTrue("com.example.domain.Order" in classNames)
    }

    @Test
    fun `matches against source file path as well`() {
        val results = ClassFilter.filter(classes, "AppConfig\\.kt")

        assertEquals(1, results.size)
        assertEquals("com.example.config.AppConfig", results.single().className.value)
    }

    @Test
    fun `returns all classes for dot-star pattern`() {
        val results = ClassFilter.filter(classes, ".*")

        assertEquals(5, results.size)
    }
}
