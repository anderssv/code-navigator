package no.f12.codenavigator.navigation

import no.f12.codenavigator.config.OutputFormat
import no.f12.codenavigator.navigation.deadcode.DeadCodeConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DeadCodeConfigTest {

    @Test
    fun `parses all properties from map`() {
        val props = mapOf(
            "filter" to "Service",
            "exclude" to "Test",
            "format" to "json",
        )

        val config = DeadCodeConfig.parse(props)

        assertTrue(config.filter!!.containsMatchIn("MyService"))
        assertTrue(config.exclude!!.containsMatchIn("MyTest"))
        assertEquals(OutputFormat.JSON, config.format)
    }

    @Test
    fun `defaults filter to null when absent`() {
        val config = DeadCodeConfig.parse(emptyMap())

        assertNull(config.filter)
    }

    @Test
    fun `defaults exclude to null when absent`() {
        val config = DeadCodeConfig.parse(emptyMap())

        assertNull(config.exclude)
    }

    @Test
    fun `defaults to TEXT format`() {
        val config = DeadCodeConfig.parse(emptyMap())

        assertEquals(OutputFormat.TEXT, config.format)
    }

    @Test
    fun `parses LLM format`() {
        val config = DeadCodeConfig.parse(mapOf("llm" to "true"))

        assertEquals(OutputFormat.LLM, config.format)
    }

    @Test
    fun `filter regex is case-insensitive`() {
        val config = DeadCodeConfig.parse(mapOf("filter" to "service"))

        assertTrue(config.filter!!.containsMatchIn("MyService"))
    }

    @Test
    fun `exclude regex is case-insensitive`() {
        val config = DeadCodeConfig.parse(mapOf("exclude" to "test"))

        assertTrue(config.exclude!!.containsMatchIn("MyTest"))
    }

    @Test
    fun `defaults classesOnly to false when absent`() {
        val config = DeadCodeConfig.parse(emptyMap())

        assertFalse(config.classesOnly)
    }

    @Test
    fun `parses classesOnly as true`() {
        val config = DeadCodeConfig.parse(mapOf("classes-only" to "true"))

        assertTrue(config.classesOnly)
    }

    @Test
    fun `parses classesOnly as false`() {
        val config = DeadCodeConfig.parse(mapOf("classes-only" to "false"))

        assertFalse(config.classesOnly)
    }

    @Test
    fun `parses exclude-annotated from comma-separated string`() {
        val config = DeadCodeConfig.parse(mapOf(
            "exclude-annotated" to "RestController,Scheduled,Component",
            "exclude-framework" to "ALL",
        ))

        assertEquals(listOf("RestController", "Scheduled", "Component"), config.excludeAnnotated)
    }

    @Test
    fun `defaults exclude-annotated to empty list when absent`() {
        val config = DeadCodeConfig.parse(mapOf("exclude-framework" to "ALL"))

        assertTrue(config.excludeAnnotated.isEmpty())
    }

    @Test
    fun `trims whitespace from exclude-annotated values`() {
        val config = DeadCodeConfig.parse(mapOf(
            "exclude-annotated" to " RestController , Scheduled ",
            "exclude-framework" to "ALL",
        ))

        assertEquals(listOf("RestController", "Scheduled"), config.excludeAnnotated)
    }

    @Test
    fun `does not support testOnly — dead code is always analyzed from prod source set`() {
        val config = DeadCodeConfig.parse(mapOf("test-only" to "true"))

        // test-only is deliberately not read by DeadCodeConfig;
        // dead code analysis only targets prod classes (test graph is a reference input)
        assertFalse(config.prodOnly)
    }

    @Test
    fun `defaults prodOnly to false when absent`() {
        val config = DeadCodeConfig.parse(emptyMap())

        assertFalse(config.prodOnly)
    }

    @Test
    fun `parses prodOnly as true`() {
        val config = DeadCodeConfig.parse(mapOf("prod-only" to "true"))

        assertTrue(config.prodOnly)
    }

    @Test
    fun `parses prodOnly as false`() {
        val config = DeadCodeConfig.parse(mapOf("prod-only" to "false"))

        assertFalse(config.prodOnly)
    }

    @Test
    fun `all frameworks active by default includes spring entry-points in excludeAnnotated`() {
        val config = DeadCodeConfig.parse(emptyMap())

        assertTrue(config.excludeAnnotated.contains("org.springframework.stereotype.Controller"))
        assertTrue(config.excludeAnnotated.contains("org.springframework.stereotype.Component"))
        assertTrue(config.excludeAnnotated.contains("org.springframework.stereotype.Service"))
    }

    @Test
    fun `exclude-framework merges with explicit exclude-annotated`() {
        val config = DeadCodeConfig.parse(mapOf(
            "exclude-annotated" to "MyCustomAnnotation",
        ))

        assertTrue(config.excludeAnnotated.contains("org.springframework.stereotype.Controller"))
        assertTrue(config.excludeAnnotated.contains("MyCustomAnnotation"))
    }

    @Test
    fun `exclude-framework=ALL results in empty excludeAnnotated`() {
        val config = DeadCodeConfig.parse(mapOf("exclude-framework" to "ALL"))

        assertTrue(config.excludeAnnotated.isEmpty())
    }

    @Test
    fun `all frameworks active by default populates modifierAnnotated`() {
        val config = DeadCodeConfig.parse(emptyMap())

        assertTrue(config.modifierAnnotated.contains("org.springframework.transaction.annotation.Transactional"))
        assertTrue(config.modifierAnnotated.contains("org.eclipse.microprofile.faulttolerance.CircuitBreaker"))
    }

    @Test
    fun `exclude-framework=ALL results in empty modifierAnnotated`() {
        val config = DeadCodeConfig.parse(mapOf("exclude-framework" to "ALL"))

        assertTrue(config.modifierAnnotated.isEmpty())
    }

    @Test
    fun `exclude-framework=spring excludes spring but keeps others`() {
        val config = DeadCodeConfig.parse(mapOf("exclude-framework" to "spring"))

        assertFalse(config.excludeAnnotated.contains("org.springframework.stereotype.Controller"))
        assertTrue(config.excludeAnnotated.contains("jakarta.ws.rs.GET"))
        assertFalse(config.modifierAnnotated.contains("org.springframework.transaction.annotation.Transactional"))
        assertTrue(config.modifierAnnotated.contains("org.eclipse.microprofile.faulttolerance.CircuitBreaker"))
    }

    @Test
    fun `excludeAnnotated does not contain modifier annotations`() {
        val config = DeadCodeConfig.parse(emptyMap())

        assertFalse(config.excludeAnnotated.contains("org.springframework.transaction.annotation.Transactional"))
        assertFalse(config.excludeAnnotated.contains("org.springframework.cache.annotation.Cacheable"))
    }

    @Test
    fun `all frameworks active by default populates supertypeEntryPoints`() {
        val config = DeadCodeConfig.parse(emptyMap())

        assertTrue(config.supertypeEntryPoints.any { it.value == "org.springframework.data.jpa.repository.JpaRepository" })
        assertTrue(config.supertypeEntryPoints.any { it.value == "org.springframework.data.repository.CrudRepository" })
    }

    @Test
    fun `exclude-framework=ALL results in empty supertypeEntryPoints`() {
        val config = DeadCodeConfig.parse(mapOf("exclude-framework" to "ALL"))

        assertTrue(config.supertypeEntryPoints.isEmpty())
    }

    @Test
    fun `exclude-framework=spring excludes spring supertypes but keeps quarkus`() {
        val config = DeadCodeConfig.parse(mapOf("exclude-framework" to "spring"))

        assertFalse(config.supertypeEntryPoints.any { it.value == "org.springframework.data.jpa.repository.JpaRepository" })
        assertTrue(config.supertypeEntryPoints.any { it.value == "io.quarkus.hibernate.orm.panache.PanacheRepository" })
    }
}
