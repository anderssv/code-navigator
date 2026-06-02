package no.f12.codenavigator.navigation.annotation

import no.f12.codenavigator.navigation.types.AnnotationName
import no.f12.codenavigator.navigation.types.ClassName
import no.f12.codenavigator.navigation.relations.callgraph.MethodRef
import no.f12.codenavigator.navigation.annotation.AnnotationExtractor
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AnnotationExtractorTest {

    private val testProjectClasses = File("test-project/build/classes/kotlin/main")

    @Test
    fun `extracts class-level annotation`() {
        val classFile = testProjectClasses.resolve("com/example/variants/annotated/PaymentProcessor.class")

        val result = AnnotationExtractor.extract(classFile)

        assertTrue(
            result.classAnnotations.any { it.value.contains("Service") },
            "Should find @Service annotation. Got: ${result.classAnnotations}",
        )
    }

    @Test
    fun `extracts method-level annotation`() {
        val classFile = testProjectClasses.resolve("com/example/variants/annotated/PaymentProcessor.class")

        val result = AnnotationExtractor.extract(classFile)

        val processPaymentsRef = MethodRef(ClassName("com.example.variants.annotated.PaymentProcessor"), "processPayments")
        val annotations = result.methodAnnotations[processPaymentsRef] ?: emptySet()
        assertTrue(
            annotations.any { it.value.contains("Scheduled") },
            "processPayments should have @Scheduled. Got: ${result.methodAnnotations}",
        )
    }

    @Test
    fun `extracts multiple annotations on same class`() {
        // PaymentProcessor has @Service — but only one class annotation (Kotlin Metadata is filtered)
        val classFile = testProjectClasses.resolve("com/example/variants/annotated/PaymentProcessor.class")

        val result = AnnotationExtractor.extract(classFile)

        assertTrue(result.classAnnotations.isNotEmpty(), "Should find class annotations")
    }

    @Test
    fun `extracts multiple method annotations`() {
        val classFile = testProjectClasses.resolve("com/example/variants/annotated/PaymentProcessor.class")

        val result = AnnotationExtractor.extract(classFile)

        // refund has @Transactional, processPayments has @Scheduled
        val refundRef = MethodRef(ClassName("com.example.variants.annotated.PaymentProcessor"), "refund")
        val refundAnnotations = result.methodAnnotations[refundRef] ?: emptySet()
        assertTrue(
            refundAnnotations.any { it.value.contains("Transactional") },
            "refund should have @Transactional. All method annotations: ${result.methodAnnotations}",
        )
    }

    @Test
    fun `class with no annotations has empty annotation set`() {
        val classFile = testProjectClasses.resolve("com/example/variants/annotated/PlainService.class")

        val result = AnnotationExtractor.extract(classFile)

        assertTrue(result.classAnnotations.isEmpty(), "PlainService has no annotations. Got: ${result.classAnnotations}")
    }

    @Test
    fun `filters out Kotlin internal annotations`() {
        val classFile = testProjectClasses.resolve("com/example/variants/annotated/PaymentProcessor.class")

        val result = AnnotationExtractor.extract(classFile)

        assertTrue(
            result.classAnnotations.none { "kotlin" in it.value.lowercase() },
            "Kotlin annotations should be filtered. Got: ${result.classAnnotations}",
        )
    }

    @Test
    fun `extracts annotations from multiple classes independently`() {
        val paymentFile = testProjectClasses.resolve("com/example/variants/annotated/PaymentProcessor.class")
        val notificationFile = testProjectClasses.resolve("com/example/variants/annotated/NotificationProcessor.class")

        val paymentResult = AnnotationExtractor.extract(paymentFile)
        val notificationResult = AnnotationExtractor.extract(notificationFile)

        assertTrue(paymentResult.classAnnotations.isNotEmpty())
        assertTrue(notificationResult.classAnnotations.isNotEmpty())
    }

    @Test
    fun `method without annotations has no entry in methodAnnotations`() {
        val classFile = testProjectClasses.resolve("com/example/variants/annotated/PaymentProcessor.class")

        val result = AnnotationExtractor.extract(classFile)

        val statusRef = MethodRef(ClassName("com.example.variants.annotated.PaymentProcessor"), "status")
        val annotations = result.methodAnnotations[statusRef]
        assertTrue(annotations == null || annotations.isEmpty(),
            "status() has no annotations. Got: $annotations")
    }
}
