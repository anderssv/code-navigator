package no.f12.codenavigator.navigation.annotation

import no.f12.codenavigator.navigation.types.AnnotationName
import no.f12.codenavigator.navigation.annotation.AnnotationQueryBuilder
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AnnotationQueryBuilderTest {

    private val testProjectClasses = File("test-project/build/classes/kotlin/main")
    private val annotatedDir = File("test-project/build/classes/kotlin/main/com/example/variants/annotated")

    @Test
    fun `finds classes with matching class-level annotation`() {
        val result = AnnotationQueryBuilder.query(listOf(annotatedDir), pattern = "Service", targets = setOf(AnnotationTarget.CLASS))

        assertTrue(result.size >= 2, "Should find PaymentProcessor and NotificationProcessor. Got: ${result.map { it.className.value }}")
        assertTrue(result.any { it.className.value == "com.example.variants.annotated.PaymentProcessor" })
    }

    @Test
    fun `finds methods with matching annotation when METHOD target is included`() {
        val result = AnnotationQueryBuilder.query(listOf(annotatedDir), pattern = "Scheduled", targets = setOf(AnnotationTarget.METHOD))

        assertTrue(result.isNotEmpty(), "Should find classes with @Scheduled methods")
        val allMethods = result.flatMap { it.matchedMethods }
        assertTrue(allMethods.any { it.method.methodName == "processPayments" },
            "Should find processPayments. Got: ${allMethods.map { it.method.methodName }}")
    }

    @Test
    fun `finds fields with matching annotation when FIELD target is included`() {
        val result = AnnotationQueryBuilder.query(listOf(annotatedDir), pattern = "Inject", targets = setOf(AnnotationTarget.FIELD))

        assertTrue(result.isNotEmpty(), "Should find classes with @Inject fields")
        val allFields = result.flatMap { it.matchedFields }
        assertTrue(allFields.any { it.field.fieldName == "gateway" },
            "Should find gateway field. Got: ${allFields.map { it.field.fieldName }}")
    }

    @Test
    fun `default targets search class, method, and field`() {
        val classResult = AnnotationQueryBuilder.query(listOf(annotatedDir), pattern = "Service")
        val methodResult = AnnotationQueryBuilder.query(listOf(annotatedDir), pattern = "Scheduled")
        val fieldResult = AnnotationQueryBuilder.query(listOf(annotatedDir), pattern = "Inject")

        assertTrue(classResult.isNotEmpty(), "Default targets should find class-level matches")
        assertTrue(methodResult.flatMap { it.matchedMethods }.isNotEmpty(), "Default targets should find method-level matches")
        assertTrue(fieldResult.flatMap { it.matchedFields }.isNotEmpty(), "Default targets should find field-level matches")
    }

    @Test
    fun `pattern is case-insensitive regex`() {
        val result = AnnotationQueryBuilder.query(listOf(annotatedDir), pattern = "service", targets = setOf(AnnotationTarget.CLASS))

        assertTrue(result.isNotEmpty(), "Case-insensitive match should find @Service classes")
    }

    @Test
    fun `returns empty result when no annotations match`() {
        val result = AnnotationQueryBuilder.query(listOf(annotatedDir), pattern = "NonExistentAnnotation", targets = setOf(AnnotationTarget.CLASS))

        assertTrue(result.isEmpty())
    }

    @Test
    fun `excludes method-only matches when METHOD target is excluded`() {
        // Transactional is only on methods, not on classes
        val result = AnnotationQueryBuilder.query(listOf(annotatedDir), pattern = "Transactional", targets = setOf(AnnotationTarget.CLASS))

        assertTrue(result.isEmpty(), "Method-only annotations should not match when METHOD target is excluded")
    }

    @Test
    fun `excludes field-only matches when FIELD target is excluded`() {
        // Inject is only on fields, not on classes or methods
        val result = AnnotationQueryBuilder.query(listOf(annotatedDir), pattern = "Inject", targets = setOf(AnnotationTarget.CLASS, AnnotationTarget.METHOD))

        assertTrue(result.isEmpty(), "Field-only annotations should not match when FIELD target is excluded")
    }

    @Test
    fun `matches multiple classes with same annotation`() {
        val result = AnnotationQueryBuilder.query(listOf(annotatedDir), pattern = "Service", targets = setOf(AnnotationTarget.CLASS))

        assertTrue(result.size >= 2, "Multiple classes have @Service. Got: ${result.map { it.className.value }}")
        val names = result.map { it.className.value }.sorted()
        assertTrue("com.example.variants.annotated.NotificationProcessor" in names)
        assertTrue("com.example.variants.annotated.PaymentProcessor" in names)
    }

    @Test
    fun `includes source file in result`() {
        val result = AnnotationQueryBuilder.query(listOf(annotatedDir), pattern = "Service", targets = setOf(AnnotationTarget.CLASS))

        assertTrue(result.all { it.sourceFile == "Annotated.kt" },
            "All annotated classes are in Annotated.kt")
    }

    @Test
    fun `method match returns class even if class has no matching class-level annotation`() {
        // PaymentProcessor's own class annotation is @Service, not @Transactional — only its refund method is
        val result = AnnotationQueryBuilder.query(listOf(annotatedDir), pattern = "Transactional", targets = setOf(AnnotationTarget.METHOD))

        assertEquals(1, result.size)
        assertEquals("com.example.variants.annotated.PaymentProcessor", result[0].className.value)
        assertEquals(1, result[0].matchedMethods.size)
        assertEquals("refund", result[0].matchedMethods[0].method.methodName)
    }

    @Test
    fun `field match returns class even if class has no matching class-level annotation`() {
        val result = AnnotationQueryBuilder.query(listOf(annotatedDir), pattern = "Inject", targets = setOf(AnnotationTarget.FIELD))

        assertEquals(1, result.size)
        assertEquals("com.example.variants.annotated.PaymentProcessor", result[0].className.value)
        assertEquals(1, result[0].matchedFields.size)
        assertEquals("gateway", result[0].matchedFields[0].field.fieldName)
    }

    @Test
    fun `finds annotations across full test-project`() {
        val result = AnnotationQueryBuilder.query(listOf(testProjectClasses), pattern = "Scheduled", targets = setOf(AnnotationTarget.METHOD))

        assertTrue(result.isNotEmpty(), "Should find @Scheduled in full test-project scan")
    }
}
