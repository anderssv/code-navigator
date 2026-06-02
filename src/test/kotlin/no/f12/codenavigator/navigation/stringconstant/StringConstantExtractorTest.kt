package no.f12.codenavigator.navigation.stringconstant

import no.f12.codenavigator.navigation.types.ClassName
import no.f12.codenavigator.navigation.stringconstant.StringConstantExtractor
import no.f12.codenavigator.navigation.stringconstant.StringConstantScanner
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StringConstantExtractorTest {

    private val testProjectClasses = File("test-project/build/classes/kotlin/main")

    @Test
    fun `extracts string constants from method body`() {
        val classFile = testProjectClasses.resolve("com/example/variants/constants/ApiRoutes.class")

        val result = StringConstantExtractor.extract(classFile)

        assertTrue(result.isNotEmpty(), "Should find string constants in ApiRoutes")
        assertTrue(result.any { it.value == "/api/v1/users" }, "Should find /api/v1/users. Got: ${result.map { it.value }}")
        assertTrue(result.all { it.className == ClassName("com.example.variants.constants.ApiRoutes") })
        assertTrue(result.all { it.sourceFile == "Constants.kt" })
    }

    @Test
    fun `extracts multiple string constants from different methods`() {
        val classFile = testProjectClasses.resolve("com/example/variants/constants/ApiRoutes.class")

        val result = StringConstantExtractor.extract(classFile)

        val values = result.map { it.value }.toSet()
        assertTrue("/api/v1/users" in values)
        assertTrue("/api/v1/orders" in values)
        assertTrue("/health" in values)
    }

    @Test
    fun `associates string constant with correct method`() {
        val classFile = testProjectClasses.resolve("com/example/variants/constants/ApiRoutes.class")

        val result = StringConstantExtractor.extract(classFile)

        val userEndpointStrings = result.filter { it.methodName == "userEndpoint" }
        assertTrue(userEndpointStrings.any { it.value == "/api/v1/users" })
    }

    @Test
    fun `returns empty list for class with no string constants in methods`() {
        // Cacheable interface has no method bodies
        val classFile = testProjectClasses.resolve("com/example/variants/hierarchy/Cacheable.class")

        val result = StringConstantExtractor.extract(classFile)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `skips constructors`() {
        val classFile = testProjectClasses.resolve("com/example/variants/constants/ApiRoutes.class")

        val result = StringConstantExtractor.extract(classFile)

        assertTrue(result.none { it.methodName == "<init>" || it.methodName == "<clinit>" },
            "Constructor strings should be skipped")
    }
}
