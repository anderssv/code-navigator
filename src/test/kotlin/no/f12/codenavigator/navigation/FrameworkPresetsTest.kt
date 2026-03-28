package no.f12.codenavigator.navigation

import no.f12.codenavigator.navigation.annotation.FrameworkPresets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FrameworkPresetsTest {

    @Test
    fun `spring preset includes Controller and Component`() {
        val annotations = FrameworkPresets.resolve("spring")

        assertTrue(annotations.contains(AnnotationName("org.springframework.stereotype.Controller")))
        assertTrue(annotations.contains(AnnotationName("org.springframework.stereotype.Component")))
        assertTrue(annotations.contains(AnnotationName("org.springframework.web.bind.annotation.RestController")))
        assertTrue(annotations.contains(AnnotationName("org.springframework.stereotype.Service")))
        assertTrue(annotations.contains(AnnotationName("org.springframework.stereotype.Repository")))
        assertTrue(annotations.contains(AnnotationName("org.springframework.context.annotation.Configuration")))
        assertTrue(annotations.contains(AnnotationName("org.springframework.context.annotation.Bean")))
    }

    @Test
    fun `unknown framework returns empty set`() {
        val annotations = FrameworkPresets.resolve("unknown-framework")

        assertTrue(annotations.isEmpty())
    }

    @Test
    fun `resolving multiple frameworks merges their annotations`() {
        val annotations = FrameworkPresets.resolveAll(listOf("spring"))

        assertTrue(annotations.contains(AnnotationName("org.springframework.stereotype.Controller")))
        assertTrue(annotations.contains(AnnotationName("org.springframework.context.annotation.Bean")))
    }

    @Test
    fun `resolving multiple frameworks with unknown returns only known`() {
        val annotations = FrameworkPresets.resolveAll(listOf("spring", "unknown"))

        assertTrue(annotations.contains(AnnotationName("org.springframework.stereotype.Controller")))
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

        assertTrue(annotations.contains(AnnotationName("jakarta.persistence.Entity")))
        assertTrue(annotations.contains(AnnotationName("jakarta.persistence.MappedSuperclass")))
    }

    @Test
    fun `spring preset includes SpringBootApplication`() {
        val annotations = FrameworkPresets.resolve("spring")

        assertTrue(annotations.contains(AnnotationName("org.springframework.boot.autoconfigure.SpringBootApplication")))
    }

    @Test
    fun `jpa preset includes Entity`() {
        val annotations = FrameworkPresets.resolve("jpa")

        assertTrue(annotations.contains(AnnotationName("jakarta.persistence.Entity")))
        assertTrue(annotations.contains(AnnotationName("jakarta.persistence.MappedSuperclass")))
        assertTrue(annotations.contains(AnnotationName("jakarta.persistence.Table")))
    }

    @Test
    fun `jackson preset includes JsonCreator`() {
        val annotations = FrameworkPresets.resolve("jackson")

        assertTrue(annotations.contains(AnnotationName("com.fasterxml.jackson.annotation.JsonCreator")))
    }

    @Test
    fun `resolving multiple distinct frameworks merges all annotations`() {
        val annotations = FrameworkPresets.resolveAll(listOf("jpa", "jackson"))

        assertTrue(annotations.contains(AnnotationName("jakarta.persistence.Entity")))
        assertTrue(annotations.contains(AnnotationName("com.fasterxml.jackson.annotation.JsonCreator")))
    }

    @Test
    fun `resolveAll with empty list returns empty set`() {
        val annotations = FrameworkPresets.resolveAll(emptyList())

        assertTrue(annotations.isEmpty())
    }

    @Test
    fun `frameworkOf returns spring for a Spring annotation`() {
        val framework = FrameworkPresets.frameworkOf(AnnotationName("org.springframework.stereotype.Controller"))

        assertEquals("spring", framework)
    }

    @Test
    fun `frameworkOf returns jpa for a JPA annotation not spring`() {
        val framework = FrameworkPresets.frameworkOf(AnnotationName("jakarta.persistence.Entity"))

        assertEquals("jpa", framework)
    }

    @Test
    fun `frameworkOf returns jackson for a Jackson annotation`() {
        val framework = FrameworkPresets.frameworkOf(AnnotationName("com.fasterxml.jackson.annotation.JsonCreator"))

        assertEquals("jackson", framework)
    }

    @Test
    fun `frameworkOf returns null for unknown annotation`() {
        val framework = FrameworkPresets.frameworkOf(AnnotationName("com.example.CustomAnnotation"))

        assertEquals(null, framework)
    }

    @Test
    fun `junit preset includes Test and BeforeEach`() {
        val annotations = FrameworkPresets.resolve("junit")

        assertTrue(annotations.contains(AnnotationName("org.junit.jupiter.api.Test")))
        assertTrue(annotations.contains(AnnotationName("org.junit.jupiter.api.BeforeEach")))
        assertTrue(annotations.contains(AnnotationName("org.junit.jupiter.api.AfterEach")))
        assertTrue(annotations.contains(AnnotationName("org.junit.jupiter.params.ParameterizedTest")))
        assertTrue(annotations.contains(AnnotationName("org.junit.jupiter.api.Disabled")))
    }

    @Test
    fun `frameworkOf returns junit for Test annotation`() {
        val framework = FrameworkPresets.frameworkOf(AnnotationName("org.junit.jupiter.api.Test"))

        assertEquals("junit", framework)
    }

    @Test
    fun `jakarta preset includes PostConstruct and PreDestroy`() {
        val annotations = FrameworkPresets.resolve("jakarta")

        assertTrue(annotations.contains(AnnotationName("jakarta.annotation.PostConstruct")))
        assertTrue(annotations.contains(AnnotationName("jakarta.annotation.PreDestroy")))
    }

    @Test
    fun `jakarta preset includes Inject and Named`() {
        val annotations = FrameworkPresets.resolve("jakarta")

        assertTrue(annotations.contains(AnnotationName("jakarta.inject.Inject")))
        assertTrue(annotations.contains(AnnotationName("jakarta.inject.Named")))
        assertTrue(annotations.contains(AnnotationName("jakarta.inject.Singleton")))
    }

    @Test
    fun `jakarta preset is available in availablePresets`() {
        val presets = FrameworkPresets.availablePresets()

        assertTrue(presets.contains("jakarta"))
    }

    @Test
    fun `frameworkOf returns jakarta for PostConstruct`() {
        val framework = FrameworkPresets.frameworkOf(AnnotationName("jakarta.annotation.PostConstruct"))

        assertEquals("jakarta", framework)
    }

    @Test
    fun `spring preset includes jakarta annotations`() {
        val annotations = FrameworkPresets.resolve("spring")

        assertTrue(annotations.contains(AnnotationName("jakarta.annotation.PostConstruct")))
        assertTrue(annotations.contains(AnnotationName("jakarta.annotation.PreDestroy")))
        assertTrue(annotations.contains(AnnotationName("jakarta.inject.Inject")))
    }

    @Test
    fun `validation preset includes jakarta validation Valid`() {
        val annotations = FrameworkPresets.resolve("validation")

        assertTrue(annotations.contains(AnnotationName("jakarta.validation.Valid")))
    }

    @Test
    fun `validation preset includes jakarta validation constraints`() {
        val annotations = FrameworkPresets.resolve("validation")

        assertTrue(annotations.contains(AnnotationName("jakarta.validation.constraints.NotBlank")))
        assertTrue(annotations.contains(AnnotationName("jakarta.validation.constraints.NotNull")))
        assertTrue(annotations.contains(AnnotationName("jakarta.validation.constraints.Size")))
        assertTrue(annotations.contains(AnnotationName("jakarta.validation.constraints.Min")))
        assertTrue(annotations.contains(AnnotationName("jakarta.validation.constraints.Max")))
        assertTrue(annotations.contains(AnnotationName("jakarta.validation.constraints.Pattern")))
        assertTrue(annotations.contains(AnnotationName("jakarta.validation.constraints.Email")))
    }

    @Test
    fun `validation preset includes hibernate validator annotations`() {
        val annotations = FrameworkPresets.resolve("validation")

        assertTrue(annotations.contains(AnnotationName("org.hibernate.validator.constraints.Length")))
        assertTrue(annotations.contains(AnnotationName("org.hibernate.validator.constraints.Range")))
        assertTrue(annotations.contains(AnnotationName("org.hibernate.validator.constraints.URL")))
    }

    @Test
    fun `validation preset is available in availablePresets`() {
        val presets = FrameworkPresets.availablePresets()

        assertTrue(presets.contains("validation"))
    }

    @Test
    fun `frameworkOf returns validation for NotBlank`() {
        val framework = FrameworkPresets.frameworkOf(AnnotationName("jakarta.validation.constraints.NotBlank"))

        assertEquals("validation", framework)
    }

    @Test
    fun `spring preset includes validation annotations`() {
        val annotations = FrameworkPresets.resolve("spring")

        assertTrue(annotations.contains(AnnotationName("jakarta.validation.Valid")))
        assertTrue(annotations.contains(AnnotationName("jakarta.validation.constraints.NotBlank")))
        assertTrue(annotations.contains(AnnotationName("jakarta.validation.constraints.NotNull")))
    }

}
