package no.f12.codenavigator.navigation.deadcode

import no.f12.codenavigator.navigation.*

import no.f12.codenavigator.navigation.relations.callgraph.MethodRef
import no.f12.codenavigator.navigation.types.AnnotationName
import no.f12.codenavigator.navigation.types.ClassName
import no.f12.codenavigator.navigation.testCallGraph
import kotlin.test.Test
import kotlin.test.assertEquals

class ConfidenceScorerTest {

    private val cls = ClassName("com.example.Foo")
    private val method = MethodRef(cls, "doSomething")

    @Test
    fun `high confidence for class with no annotations and no test refs`() {
        val result = ConfidenceScorer.score(cls, null, null, false, emptyMap(), emptyMap(), emptyMap())

        assertEquals(DeadCodeConfidence.HIGH, result)
    }

    @Test
    fun `low confidence for class with annotations`() {
        val annotations = mapOf(cls to setOf(AnnotationName("org.springframework.stereotype.Component")))

        val result = ConfidenceScorer.score(cls, null, null, false, annotations, emptyMap(), emptyMap())

        assertEquals(DeadCodeConfidence.LOW, result)
    }

    @Test
    fun `low confidence for method with annotations`() {
        val methodAnnotations = mapOf(method to setOf(AnnotationName("org.junit.jupiter.api.Test")))

        val result = ConfidenceScorer.score(cls, method, null, false, emptyMap(), methodAnnotations, emptyMap())

        assertEquals(DeadCodeConfidence.LOW, result)
    }

    @Test
    fun `low confidence for method on class with external interfaces`() {
        val externalInterfaces = mapOf(cls to setOf(ClassName("org.springframework.data.jpa.repository.JpaRepository")))

        val result = ConfidenceScorer.score(cls, method, null, false, emptyMap(), emptyMap(), externalInterfaces)

        assertEquals(DeadCodeConfidence.LOW, result)
    }

    @Test
    fun `medium confidence when referenced in tests only`() {
        val testGraph = testCallGraph()

        val result = ConfidenceScorer.score(cls, null, testGraph, true, emptyMap(), emptyMap(), emptyMap())

        assertEquals(DeadCodeConfidence.MEDIUM, result)
    }

    @Test
    fun `low confidence for modifier annotation on class`() {
        val classAnnotations = mapOf(cls to setOf(AnnotationName("com.example.Generated")))

        val result = ConfidenceScorer.score(
            cls, null, null, false, classAnnotations, emptyMap(), emptyMap(),
            modifierAnnotated = setOf("Generated"),
        )

        assertEquals(DeadCodeConfidence.LOW, result)
    }

    @Test
    fun `low confidence for modifier annotation on method`() {
        val methodAnnotations = mapOf(method to setOf(AnnotationName("com.example.Generated")))

        val result = ConfidenceScorer.score(
            cls, method, null, false, emptyMap(), methodAnnotations, emptyMap(),
            modifierAnnotated = setOf("Generated"),
        )

        assertEquals(DeadCodeConfidence.LOW, result)
    }

    @Test
    fun `modifier annotation checked before other rules`() {
        val classAnnotations = mapOf(cls to setOf(AnnotationName("com.example.Generated")))
        val testGraph = testCallGraph()

        val result = ConfidenceScorer.score(
            cls, null, testGraph, true, classAnnotations, emptyMap(), emptyMap(),
            modifierAnnotated = setOf("Generated"),
        )

        assertEquals(DeadCodeConfidence.LOW, result)
    }

    @Test
    fun `high confidence for method with no annotations and no test refs`() {
        val result = ConfidenceScorer.score(cls, method, null, false, emptyMap(), emptyMap(), emptyMap())

        assertEquals(DeadCodeConfidence.HIGH, result)
    }

    @Test
    fun `low confidence for const val holder class with no references`() {
        val result = ConfidenceScorer.score(
            cls, null, null, false, emptyMap(), emptyMap(), emptyMap(),
            constValHolders = setOf(cls),
        )

        assertEquals(DeadCodeConfidence.LOW, result)
    }

    @Test
    fun `const val holder downgrade does not apply to methods`() {
        val result = ConfidenceScorer.score(
            cls, method, null, false, emptyMap(), emptyMap(), emptyMap(),
            constValHolders = setOf(cls),
        )

        assertEquals(DeadCodeConfidence.HIGH, result)
    }
}
