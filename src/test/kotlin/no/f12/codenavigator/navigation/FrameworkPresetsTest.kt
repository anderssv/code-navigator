package no.f12.codenavigator.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FrameworkPresetsTest {

    // [TEST] Spring preset returns expected annotations
    @Test
    fun `spring preset includes Controller and Component`() {
        val annotations = FrameworkPresets.resolve("spring")

        assertTrue(annotations.contains("Controller"))
        assertTrue(annotations.contains("Component"))
        assertTrue(annotations.contains("RestController"))
        assertTrue(annotations.contains("Service"))
        assertTrue(annotations.contains("Repository"))
        assertTrue(annotations.contains("Configuration"))
        assertTrue(annotations.contains("Bean"))
    }

    @Test
    fun `unknown framework returns empty set`() {
        val annotations = FrameworkPresets.resolve("unknown-framework")

        assertTrue(annotations.isEmpty())
    }

    @Test
    fun `resolving multiple frameworks merges their annotations`() {
        val annotations = FrameworkPresets.resolveAll(listOf("spring"))

        assertTrue(annotations.contains("Controller"))
        assertTrue(annotations.contains("Bean"))
    }

    @Test
    fun `resolving multiple frameworks with unknown returns only known`() {
        val annotations = FrameworkPresets.resolveAll(listOf("spring", "unknown"))

        assertTrue(annotations.contains("Controller"))
        assertTrue(annotations.isNotEmpty())
    }

    @Test
    fun `framework names are case-insensitive`() {
        val upper = FrameworkPresets.resolve("Spring")
        val lower = FrameworkPresets.resolve("spring")
        val mixed = FrameworkPresets.resolve("SPRING")

        assertEquals(lower, upper)
        assertEquals(lower, mixed)
    }

    @Test
    fun `available presets includes spring`() {
        val presets = FrameworkPresets.availablePresets()

        assertTrue(presets.contains("spring"))
    }

    @Test
    fun `spring preset includes JPA annotations`() {
        val annotations = FrameworkPresets.resolve("spring")

        assertTrue(annotations.contains("Entity"))
        assertTrue(annotations.contains("MappedSuperclass"))
    }

    @Test
    fun `spring preset includes SpringBootApplication`() {
        val annotations = FrameworkPresets.resolve("spring")

        assertTrue(annotations.contains("SpringBootApplication"))
    }

    @Test
    fun `jpa preset includes Entity`() {
        val annotations = FrameworkPresets.resolve("jpa")

        assertTrue(annotations.contains("Entity"))
        assertTrue(annotations.contains("MappedSuperclass"))
    }

    @Test
    fun `jackson preset includes JsonCreator`() {
        val annotations = FrameworkPresets.resolve("jackson")

        assertTrue(annotations.contains("JsonCreator"))
    }

    @Test
    fun `resolving multiple distinct frameworks merges all annotations`() {
        val annotations = FrameworkPresets.resolveAll(listOf("jpa", "jackson"))

        assertTrue(annotations.contains("Entity"))
        assertTrue(annotations.contains("JsonCreator"))
    }

    @Test
    fun `resolveAll with empty list returns empty set`() {
        val annotations = FrameworkPresets.resolveAll(emptyList())

        assertTrue(annotations.isEmpty())
    }

    @Test
    fun `frameworkOf returns spring for a Spring annotation`() {
        val framework = FrameworkPresets.frameworkOf("Controller")

        assertEquals("spring", framework)
    }

    @Test
    fun `frameworkOf returns jpa for a JPA annotation not spring`() {
        val framework = FrameworkPresets.frameworkOf("Entity")

        assertEquals("jpa", framework)
    }

    @Test
    fun `frameworkOf returns jackson for a Jackson annotation`() {
        val framework = FrameworkPresets.frameworkOf("JsonCreator")

        assertEquals("jackson", framework)
    }

    @Test
    fun `frameworkOf returns null for unknown annotation`() {
        val framework = FrameworkPresets.frameworkOf("CustomAnnotation")

        assertEquals(null, framework)
    }

    @Test
    fun `junit preset includes Test and BeforeEach`() {
        val annotations = FrameworkPresets.resolve("junit")

        assertTrue(annotations.contains("Test"))
        assertTrue(annotations.contains("BeforeEach"))
        assertTrue(annotations.contains("AfterEach"))
        assertTrue(annotations.contains("ParameterizedTest"))
        assertTrue(annotations.contains("Disabled"))
    }

    @Test
    fun `frameworkOf returns junit for Test annotation`() {
        val framework = FrameworkPresets.frameworkOf("Test")

        assertEquals("junit", framework)
    }
}
