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
        val result = AnnotationQueryBuilder.query(listOf(annotatedDir), pattern = "Service", methods = false)

        assertTrue(result.size >= 2, "Should find PaymentProcessor and NotificationProcessor. Got: ${result.map { it.className.value }}")
        assertTrue(result.any { it.className.value == "com.example.variants.annotated.PaymentProcessor" })
    }

    @Test
    fun `finds methods with matching annotation when methods=true`() {
        val result = AnnotationQueryBuilder.query(listOf(annotatedDir), pattern = "Scheduled", methods = true)

        assertTrue(result.isNotEmpty(), "Should find classes with @Scheduled methods")
        val allMethods = result.flatMap { it.matchedMethods }
        assertTrue(allMethods.any { it.method.methodName == "processPayments" },
            "Should find processPayments. Got: ${allMethods.map { it.method.methodName }}")
    }

    @Test
    fun `pattern is case-insensitive regex`() {
        val result = AnnotationQueryBuilder.query(listOf(annotatedDir), pattern = "service", methods = false)

        assertTrue(result.isNotEmpty(), "Case-insensitive match should find @Service classes")
    }

    @Test
    fun `returns empty result when no annotations match`() {
        val result = AnnotationQueryBuilder.query(listOf(annotatedDir), pattern = "NonExistentAnnotation", methods = false)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `excludes method-only matches when methods=false`() {
        // Transactional is only on methods, not on classes
        val result = AnnotationQueryBuilder.query(listOf(annotatedDir), pattern = "Transactional", methods = false)

        assertTrue(result.isEmpty(), "Method-only annotations should not match when methods=false")
    }

    @Test
    fun `matches multiple classes with same annotation`() {
        val result = AnnotationQueryBuilder.query(listOf(annotatedDir), pattern = "Service", methods = false)

        assertTrue(result.size >= 2, "Multiple classes have @Service. Got: ${result.map { it.className.value }}")
        val names = result.map { it.className.value }.sorted()
        assertTrue("com.example.variants.annotated.NotificationProcessor" in names)
        assertTrue("com.example.variants.annotated.PaymentProcessor" in names)
    }

    @Test
    fun `includes source file in result`() {
        val result = AnnotationQueryBuilder.query(listOf(annotatedDir), pattern = "Service", methods = false)

        assertTrue(result.all { it.sourceFile == "Annotated.kt" },
            "All annotated classes are in Annotated.kt")
    }

    @Test
    fun `method match returns class even if class has no matching class-level annotation`() {
        // PlainService has no class annotations but could have method annotations if we add one
        // Use Transactional which is only on PaymentProcessor.refund
        val result = AnnotationQueryBuilder.query(listOf(annotatedDir), pattern = "Transactional", methods = true)

        assertEquals(1, result.size)
        assertEquals("com.example.variants.annotated.PaymentProcessor", result[0].className.value)
        assertEquals(1, result[0].matchedMethods.size)
        assertEquals("refund", result[0].matchedMethods[0].method.methodName)
    }

    @Test
    fun `finds annotations across full test-project`() {
        val result = AnnotationQueryBuilder.query(listOf(testProjectClasses), pattern = "Scheduled", methods = true)

        assertTrue(result.isNotEmpty(), "Should find @Scheduled in full test-project scan")
    }
}
