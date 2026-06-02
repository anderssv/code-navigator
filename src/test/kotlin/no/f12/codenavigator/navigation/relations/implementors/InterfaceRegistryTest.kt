package no.f12.codenavigator.navigation.relations.implementors

import no.f12.codenavigator.navigation.types.ClassName
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InterfaceRegistryTest {

    private val testProjectClasses = File("test-project/build/classes/kotlin/main")

    @Test
    fun `finds implementors of an interface`() {
        val registry = InterfaceRegistry.build(listOf(testProjectClasses)).data

        val implementors = registry.implementorsOf(ClassName("com.example.domain.NotificationSender"))
        val names = implementors.map { it.className.value }.sorted()
        assertTrue(names.contains("com.example.infra.EmailNotificationSender"))
        assertTrue(names.contains("com.example.infra.NoOpNotificationSender"))
    }

    @Test
    fun `returns empty list for interface with no implementors`() {
        val registry = InterfaceRegistry.build(listOf(testProjectClasses)).data

        assertTrue(registry.implementorsOf(ClassName("com.example.nonexistent.Missing")).isEmpty())
    }

    @Test
    fun `finds multiple implementors of the same interface`() {
        val registry = InterfaceRegistry.build(listOf(testProjectClasses)).data

        val names = registry.implementorsOf(ClassName("com.example.domain.NotificationSender")).map { it.className.value }
        assertTrue(names.size >= 2, "NotificationSender should have at least 2 implementors, got: $names")
    }

    @Test
    fun `class implementing multiple interfaces appears under each`() {
        val registry = InterfaceRegistry.build(listOf(testProjectClasses)).data

        // ConcreteService implements both Cacheable and Validatable
        val cacheableImpls = registry.implementorsOf(ClassName("com.example.variants.hierarchy.Cacheable")).map { it.className.value }
        val validatableImpls = registry.implementorsOf(ClassName("com.example.variants.hierarchy.Validatable")).map { it.className.value }
        assertTrue("com.example.variants.hierarchy.ConcreteService" in cacheableImpls)
        assertTrue("com.example.variants.hierarchy.ConcreteService" in validatableImpls)
    }

    @Test
    fun `findInterfaces matches pattern case-insensitively`() {
        val registry = InterfaceRegistry.build(listOf(testProjectClasses)).data

        val matches = registry.findInterfaces("notificationsender").map { it.value }
        assertTrue("com.example.domain.NotificationSender" in matches)
    }

    @Test
    fun `skips synthetic and lambda classes`() {
        val registry = InterfaceRegistry.build(listOf(testProjectClasses)).data

        // UserRoute has lambdas (UserRoute$handleReset$1) — they should not appear as implementors
        val allImplementors = registry.implementorsOf(ClassName("com.example.domain.UserRepository"))
        val names = allImplementors.map { it.className.value }
        assertTrue(names.none { "\$" in it && it.last().isDigit() }, "Lambda classes should be filtered: $names")
    }

    @Test
    fun `returns implementors sorted by class name`() {
        val registry = InterfaceRegistry.build(listOf(testProjectClasses)).data

        val names = registry.implementorsOf(ClassName("com.example.domain.NotificationSender")).map { it.className.value }
        assertEquals(names.sorted(), names, "Implementors should be sorted alphabetically")
    }

    @Test
    fun `externalInterfacesOf returns empty for classes with only in-scope interfaces`() {
        val registry = InterfaceRegistry.build(listOf(testProjectClasses)).data
        // InMemoryUserRepository implements UserRepository — both are in project
        val projectClasses = setOf(
            ClassName("com.example.infra.InMemoryUserRepository"),
            ClassName("com.example.domain.UserRepository"),
        )

        val external = registry.externalInterfacesOf(projectClasses)

        assertTrue(
            external[ClassName("com.example.infra.InMemoryUserRepository")]?.isEmpty() ?: true,
            "In-scope interface should not appear in externalInterfacesOf",
        )
    }

    @Test
    fun `externalInterfacesOf returns external interfaces not in project`() {
        val registry = InterfaceRegistry.build(listOf(testProjectClasses)).data
        // InMemoryUserRepository implements UserRepository — if UserRepository is NOT in projectClasses, it's external
        val projectClasses = setOf(ClassName("com.example.infra.InMemoryUserRepository"))

        val external = registry.externalInterfacesOf(projectClasses)

        assertEquals(
            setOf(ClassName("com.example.domain.UserRepository")),
            external[ClassName("com.example.infra.InMemoryUserRepository")],
        )
    }

    @Test
    fun `interfacesOf returns interfaces implemented by a class`() {
        val registry = InterfaceRegistry.build(listOf(testProjectClasses)).data

        val interfaces = registry.interfacesOf(ClassName("com.example.variants.hierarchy.ConcreteService"))

        assertTrue(ClassName("com.example.variants.hierarchy.Cacheable") in interfaces)
        assertTrue(ClassName("com.example.variants.hierarchy.Validatable") in interfaces)
    }

    @Test
    fun `interfacesOf returns empty set for unknown class`() {
        val registry = InterfaceRegistry.build(listOf(testProjectClasses)).data

        assertTrue(registry.interfacesOf(ClassName("com.example.Unknown")).isEmpty())
    }

    @Test
    fun `interfacesOf returns superclass along with interfaces`() {
        val registry = InterfaceRegistry.build(listOf(testProjectClasses)).data

        val supertypes = registry.interfacesOf(ClassName("com.example.variants.hierarchy.ConcreteService"))

        assertTrue(ClassName("com.example.variants.hierarchy.BaseService") in supertypes,
            "Should include superclass. Got: $supertypes")
    }

    @Test
    fun `implementorMap returns class names for interface`() {
        val registry = InterfaceRegistry.build(listOf(testProjectClasses)).data
        val map = registry.implementorMap()

        val impls = map[ClassName("com.example.domain.NotificationSender")] ?: emptySet()
        assertTrue(ClassName("com.example.infra.EmailNotificationSender") in impls)
        assertTrue(ClassName("com.example.infra.NoOpNotificationSender") in impls)
    }

    @Test
    fun `classToInterfacesMap returns reverse mapping`() {
        val registry = InterfaceRegistry.build(listOf(testProjectClasses)).data
        val map = registry.classToInterfacesMap()

        val interfaces = map[ClassName("com.example.infra.InMemoryUserRepository")] ?: emptySet()
        assertTrue(ClassName("com.example.domain.UserRepository") in interfaces)
    }
}
