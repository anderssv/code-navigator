package no.f12.codenavigator.navigation.relations.hierarchy

import no.f12.codenavigator.navigation.types.ClassName
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TypeHierarchyBuilderTest {

    private val testProjectClasses = File("test-project/build/classes/kotlin/main")

    @Test
    fun `builds hierarchy for class with no supertypes beyond Object`() {
        val results = TypeHierarchyBuilder.build(listOf(testProjectClasses), "EventSender", projectOnly = true)

        assertTrue(results.isNotEmpty())
        val result = results.first { it.className.value == "com.example.infra.EventSender" }
        assertTrue(result.supertypes.isEmpty(), "EventSender has no project supertypes")
    }

    @Test
    fun `builds hierarchy showing direct superclass`() {
        val results = TypeHierarchyBuilder.build(listOf(testProjectClasses), "ConcreteService", projectOnly = true)

        assertEquals(1, results.size)
        val supertypes = results.first().supertypes
        val superclass = supertypes.firstOrNull { it.kind == SupertypeKind.CLASS }
        assertEquals(ClassName("com.example.variants.hierarchy.BaseService"), superclass?.className)
    }

    @Test
    fun `builds hierarchy showing direct interfaces`() {
        val results = TypeHierarchyBuilder.build(listOf(testProjectClasses), "ConcreteService", projectOnly = true)

        assertEquals(1, results.size)
        val interfaces = results.first().supertypes.filter { it.kind == SupertypeKind.INTERFACE }
        val names = interfaces.map { it.className.value }.toSet()
        assertTrue("com.example.variants.hierarchy.Cacheable" in names)
        assertTrue("com.example.variants.hierarchy.Validatable" in names)
    }

    @Test
    fun `walks interface chain recursively`() {
        // FetchService implements Fetchable, which extends Cacheable
        val results = TypeHierarchyBuilder.build(listOf(testProjectClasses), "FetchService", projectOnly = true)

        assertEquals(1, results.size)
        val fetchable = results.first().supertypes.firstOrNull {
            it.className.value == "com.example.variants.hierarchy.Fetchable"
        }
        assertTrue(fetchable != null, "Should find Fetchable interface")
        val cacheable = fetchable.supertypes.firstOrNull {
            it.className.value == "com.example.variants.hierarchy.Cacheable"
        }
        assertTrue(cacheable != null, "Fetchable should show Cacheable as supertype")
    }

    @Test
    fun `combines superclass and interfaces`() {
        val results = TypeHierarchyBuilder.build(listOf(testProjectClasses), "ConcreteService", projectOnly = true)

        val supertypes = results.first().supertypes
        assertTrue(supertypes.size >= 3, "Should have BaseService + Cacheable + Validatable, got: ${supertypes.map { it.className.value }}")
    }

    @Test
    fun `shows implementors for an interface`() {
        val results = TypeHierarchyBuilder.build(listOf(testProjectClasses), "Validatable", projectOnly = true)

        val validatableResult = results.first { it.className.value == "com.example.variants.hierarchy.Validatable" }
        val implNames = validatableResult.implementors.map { it.className.value }.toSet()
        assertTrue("com.example.variants.hierarchy.ConcreteService" in implNames,
            "ConcreteService should implement Validatable. Got: $implNames")
    }

    @Test
    fun `matches classes by pattern case-insensitively`() {
        val results = TypeHierarchyBuilder.build(listOf(testProjectClasses), "concreteservice", projectOnly = true)

        assertEquals(1, results.size)
        assertEquals(ClassName("com.example.variants.hierarchy.ConcreteService"), results.first().className)
    }

    @Test
    fun `returns empty list when pattern matches nothing`() {
        val results = TypeHierarchyBuilder.build(listOf(testProjectClasses), "NonExistentXyz123", projectOnly = true)

        assertTrue(results.isEmpty())
    }

    @Test
    fun `projectOnly true stops recursion at non-project types`() {
        // ConcreteService extends BaseService which extends Object (non-project)
        val results = TypeHierarchyBuilder.build(listOf(testProjectClasses), "ConcreteService", projectOnly = true)

        val baseService = results.first().supertypes.first { it.className.value == "com.example.variants.hierarchy.BaseService" }
        // BaseService's superclass is Object — should not recurse into it
        assertTrue(baseService.supertypes.isEmpty(), "Should not recurse into non-project types")
    }

    @Test
    fun `shows sealed class implementors`() {
        val results = TypeHierarchyBuilder.build(listOf(testProjectClasses), "UserError", projectOnly = true)

        val userError = results.firstOrNull { it.className.value == "com.example.domain.UserError" }
        assertTrue(userError != null, "Should find UserError")
        val implNames = userError.implementors.map { it.className.value }.toSet()
        assertTrue("com.example.domain.UserError\$NotFound" in implNames)
        assertTrue("com.example.domain.UserError\$ValidationFailed" in implNames)
    }
}
