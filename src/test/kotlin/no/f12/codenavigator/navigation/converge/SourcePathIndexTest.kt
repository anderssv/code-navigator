package no.f12.codenavigator.navigation.converge

import no.f12.codenavigator.navigation.classinfo.ClassInfo
import no.f12.codenavigator.navigation.types.ClassName
import no.f12.codenavigator.navigation.types.PackageName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SourcePathIndexTest {

    private fun classInfo(className: String, sourceFileName: String) = ClassInfo(
        className = ClassName(className),
        sourceFileName = sourceFileName,
        reconstructedSourcePath = "${className.substringBeforeLast(".").replace(".", "/")}/$sourceFileName",
        isUserDefinedClass = true,
    )

    @Test
    fun `resolves a git path that ends with the reconstructed source path`() {
        val index = SourcePathIndex.from(listOf(classInfo("com.example.domain.Order", "Order.kt")))

        val resolved = index.resolvePackage("src/main/kotlin/com/example/domain/Order.kt")

        assertEquals(PackageName("com.example.domain"), resolved)
    }

    @Test
    fun `returns null for a path with no matching class`() {
        val index = SourcePathIndex.from(listOf(classInfo("com.example.domain.Order", "Order.kt")))

        assertNull(index.resolvePackage("src/main/resources/application.yml"))
    }

    @Test
    fun `disambiguates same filename in different packages via suffix match`() {
        val index = SourcePathIndex.from(
            listOf(
                classInfo("com.example.web.Config", "Config.kt"),
                classInfo("com.example.infra.Config", "Config.kt"),
            ),
        )

        assertEquals(PackageName("com.example.web"), index.resolvePackage("src/main/kotlin/com/example/web/Config.kt"))
        assertEquals(PackageName("com.example.infra"), index.resolvePackage("src/main/kotlin/com/example/infra/Config.kt"))
    }

    @Test
    fun `multiple classes in the same file resolve to the same package`() {
        val index = SourcePathIndex.from(
            listOf(
                classInfo("com.example.domain.Order", "Types.kt"),
                classInfo("com.example.domain.OrderLine", "Types.kt"),
            ),
        )

        assertEquals(PackageName("com.example.domain"), index.resolvePackage("src/main/kotlin/com/example/domain/Types.kt"))
    }
}
