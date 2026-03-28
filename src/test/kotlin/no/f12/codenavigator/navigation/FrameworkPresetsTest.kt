package no.f12.codenavigator.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FrameworkPresetsTest {

    @Test
    fun `spring preset includes Controller and Component`() {
        val annotations = FrameworkPresets.resolve("spring")

        assertTrue(annotations.contains("org.springframework.stereotype.Controller"))
        assertTrue(annotations.contains("org.springframework.stereotype.Component"))
        assertTrue(annotations.contains("org.springframework.web.bind.annotation.RestController"))
        assertTrue(annotations.contains("org.springframework.stereotype.Service"))
        assertTrue(annotations.contains("org.springframework.stereotype.Repository"))
        assertTrue(annotations.contains("org.springframework.context.annotation.Configuration"))
        assertTrue(annotations.contains("org.springframework.context.annotation.Bean"))
    }

    @Test
    fun `unknown framework returns empty set`() {
        val annotations = FrameworkPresets.resolve("unknown-framework")

        assertTrue(annotations.isEmpty())
    }

    @Test
    fun `resolving multiple frameworks merges their annotations`() {
        val annotations = FrameworkPresets.resolveAll(listOf("spring"))

        assertTrue(annotations.contains("org.springframework.stereotype.Controller"))
        assertTrue(annotations.contains("org.springframework.context.annotation.Bean"))
    }

    @Test
    fun `resolving multiple frameworks with unknown returns only known`() {
        val annotations = FrameworkPresets.resolveAll(listOf("spring", "unknown"))

        assertTrue(annotations.contains("org.springframework.stereotype.Controller"))
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

        assertTrue(annotations.contains("jakarta.persistence.Entity"))
        assertTrue(annotations.contains("jakarta.persistence.MappedSuperclass"))
    }

    @Test
    fun `spring preset includes SpringBootApplication`() {
        val annotations = FrameworkPresets.resolve("spring")

        assertTrue(annotations.contains("org.springframework.boot.autoconfigure.SpringBootApplication"))
    }

    @Test
    fun `jpa preset includes Entity`() {
        val annotations = FrameworkPresets.resolve("jpa")

        assertTrue(annotations.contains("jakarta.persistence.Entity"))
        assertTrue(annotations.contains("jakarta.persistence.MappedSuperclass"))
    }

    @Test
    fun `jackson preset includes JsonCreator`() {
        val annotations = FrameworkPresets.resolve("jackson")

        assertTrue(annotations.contains("com.fasterxml.jackson.annotation.JsonCreator"))
    }

    @Test
    fun `resolving multiple distinct frameworks merges all annotations`() {
        val annotations = FrameworkPresets.resolveAll(listOf("jpa", "jackson"))

        assertTrue(annotations.contains("jakarta.persistence.Entity"))
        assertTrue(annotations.contains("com.fasterxml.jackson.annotation.JsonCreator"))
    }

    @Test
    fun `resolveAll with empty list returns empty set`() {
        val annotations = FrameworkPresets.resolveAll(emptyList())

        assertTrue(annotations.isEmpty())
    }

    @Test
    fun `frameworkOf returns spring for a Spring annotation`() {
        val framework = FrameworkPresets.frameworkOf("org.springframework.stereotype.Controller")

        assertEquals("spring", framework)
    }

    @Test
    fun `frameworkOf returns jpa for a JPA annotation not spring`() {
        val framework = FrameworkPresets.frameworkOf("jakarta.persistence.Entity")

        assertEquals("jpa", framework)
    }

    @Test
    fun `frameworkOf returns jackson for a Jackson annotation`() {
        val framework = FrameworkPresets.frameworkOf("com.fasterxml.jackson.annotation.JsonCreator")

        assertEquals("jackson", framework)
    }

    @Test
    fun `frameworkOf returns null for unknown annotation`() {
        val framework = FrameworkPresets.frameworkOf("com.example.CustomAnnotation")

        assertEquals(null, framework)
    }

    @Test
    fun `junit preset includes Test and BeforeEach`() {
        val annotations = FrameworkPresets.resolve("junit")

        assertTrue(annotations.contains("org.junit.jupiter.api.Test"))
        assertTrue(annotations.contains("org.junit.jupiter.api.BeforeEach"))
        assertTrue(annotations.contains("org.junit.jupiter.api.AfterEach"))
        assertTrue(annotations.contains("org.junit.jupiter.params.ParameterizedTest"))
        assertTrue(annotations.contains("org.junit.jupiter.api.Disabled"))
    }

    @Test
    fun `frameworkOf returns junit for Test annotation`() {
        val framework = FrameworkPresets.frameworkOf("org.junit.jupiter.api.Test")

        assertEquals("junit", framework)
    }
}
