package no.f12.codenavigator.navigation.dsm

import no.f12.codenavigator.navigation.types.ClassName
import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Uses real compiler output from test-project so the signature/body distinction is validated against
 * actual descriptors rather than hand-built bytecode.
 */
class SignatureTypeScannerTest {

    private val classesDir = File("test-project/build/classes/kotlin/main")

    private val signatureTypes = SignatureTypeScanner.scan(listOf(classesDir))

    private fun typesNamedBy(className: String): Set<ClassName> =
        signatureTypes[ClassName(className)].orEmpty()

    @Test
    fun `a constructor parameter type is a signature reference`() {
        val types = typesNamedBy("com.example.adapters.JdbcUserRepository")

        assertTrue(
            ClassName("java.sql.Connection") in types,
            "expected java.sql.Connection in signature types, got $types",
        )
    }

    @Test
    fun `a type used only inside a method body is not a signature reference`() {
        val types = typesNamedBy("com.example.adapters.SqlHealthCheck")

        assertFalse(
            types.any { it.value.startsWith("java.sql") },
            "SqlHealthCheck only touches java.sql inside a method body, got $types",
        )
    }

    @Test
    fun `an implemented interface is a signature reference`() {
        val types = typesNamedBy("com.example.adapters.JdbcUserRepository")

        assertTrue(ClassName("com.example.domain.UserRepository") in types, "got $types")
    }

    @Test
    fun `a method parameter and return type are signature references`() {
        val types = typesNamedBy("com.example.adapters.JdbcUserRepository")

        assertTrue(ClassName("com.example.domain.User") in types, "return type of findById, got $types")
    }
}
