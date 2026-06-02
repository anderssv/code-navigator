package no.f12.codenavigator.navigation.symbol

import no.f12.codenavigator.navigation.types.ClassName
import no.f12.codenavigator.navigation.symbol.SymbolExtractor
import no.f12.codenavigator.navigation.symbol.SymbolKind
import no.f12.codenavigator.navigation.symbol.SymbolScanner
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SymbolExtractorTest {

    private val testProjectClasses = File("test-project/build/classes/kotlin/main")

    @Test
    fun `extracts public methods as METHOD symbols`() {
        val classFile = testProjectClasses.resolve("com/example/domain/UserFormatter.class")

        val symbols = SymbolExtractor.extract(classFile)

        val methods = symbols.filter { it.kind == SymbolKind.METHOD }
        assertTrue(methods.isNotEmpty(), "Should find methods")
        assertTrue(methods.all { it.className == ClassName("com.example.domain.UserFormatter") })
        assertTrue(methods.all { it.sourceFile == "UserFormatter.kt" })
    }

    @Test
    fun `extracts fields as FIELD symbols`() {
        // User data class has fields: id, name, email, active
        val classFile = testProjectClasses.resolve("com/example/domain/User.class")

        val symbols = SymbolExtractor.extract(classFile)

        val fields = symbols.filter { it.kind == SymbolKind.FIELD }
        val fieldNames = fields.map { it.symbolName }.toSet()
        assertTrue("id" in fieldNames, "Should find 'id' field. Got: $fieldNames")
        assertTrue("name" in fieldNames, "Should find 'name' field. Got: $fieldNames")
        assertTrue("email" in fieldNames, "Should find 'email' field. Got: $fieldNames")
    }

    @Test
    fun `filters out constructors`() {
        val classFile = testProjectClasses.resolve("com/example/domain/UserFormatter.class")

        val symbols = SymbolExtractor.extract(classFile)

        assertTrue(symbols.none { it.symbolName == "<init>" || it.symbolName == "<clinit>" })
    }

    @Test
    fun `filters out synthetic and access bridge methods`() {
        // UserService has lambdas that generate access$ methods
        val classFile = testProjectClasses.resolve("com/example/services/UserService.class")

        val symbols = SymbolExtractor.extract(classFile)

        assertTrue(symbols.none { "access\$" in it.symbolName },
            "Should filter access bridges. Got: ${symbols.map { it.symbolName }}")
        assertTrue(symbols.none { it.symbolName.contains("\$lambda\$") },
            "Should filter lambda methods. Got: ${symbols.map { it.symbolName }}")
    }

    @Test
    fun `filters out Kotlin property accessors for fields`() {
        // User data class has fields — getters/setters should be filtered
        val classFile = testProjectClasses.resolve("com/example/domain/User.class")

        val symbols = SymbolExtractor.extract(classFile)

        val symbolNames = symbols.map { it.symbolName }
        assertTrue("id" in symbolNames, "Field 'id' should be present")
        assertTrue("getId" !in symbolNames, "Getter 'getId' should be filtered")
    }

    @Test
    fun `filters out data class generated methods`() {
        val classFile = testProjectClasses.resolve("com/example/domain/User.class")

        val symbols = SymbolExtractor.extract(classFile)

        val names = symbols.map { it.symbolName }
        assertTrue("component1" !in names, "component1 should be filtered")
        assertTrue("copy" !in names, "copy should be filtered")
        assertTrue("toString" !in names, "toString should be filtered")
        assertTrue("hashCode" !in names, "hashCode should be filtered")
        assertTrue("equals" !in names, "equals should be filtered")
    }

    @Test
    fun `extracts static methods from Kt facade classes`() {
        val classFile = testProjectClasses.resolve("com/example/variants/moveclass/original/MetricsKt.class")

        val symbols = SymbolExtractor.extract(classFile)

        assertTrue(symbols.isNotEmpty(), "Should find top-level members in Kt facade")
        assertTrue(symbols.all { it.className == ClassName("com.example.variants.moveclass.original.MetricsKt") })
    }

    @Test
    fun `filters out companion object INSTANCE field`() {
        val classFile = testProjectClasses.resolve("com/example/infra/EventSender\$Companion.class")

        val symbols = SymbolExtractor.extract(classFile)

        assertTrue(symbols.none { it.symbolName == "INSTANCE" },
            "INSTANCE field should be filtered. Got: ${symbols.map { it.symbolName }}")
    }

    @Test
    fun `handles class with no user methods or fields`() {
        // NoOpNotificationSender overrides 'send' which might be filtered or kept
        val classFile = testProjectClasses.resolve("com/example/infra/NoOpNotificationSender.class")

        val symbols = SymbolExtractor.extract(classFile)

        // Should have 'send' method at minimum
        assertTrue(symbols.any { it.symbolName == "send" })
    }
}
