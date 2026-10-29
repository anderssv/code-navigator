package no.f12.codenavigator.navigation.refactor

import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A test that mirrors its subject's package (ArchUnit rules often require it) moves along when asked to. */
class MoveClassMirrorTestsTest {

    @TempDir
    lateinit var dir: File

    private val main get() = File(dir, "src/main/kotlin")
    private val test get() = File(dir, "src/test/kotlin")

    private fun write(root: File, path: String, content: String) = File(root, path).apply { parentFile.mkdirs(); writeText(content) }

    private fun setUp() {
        write(main, "a/endpoint/Mapper.kt", "package a.endpoint\n\nclass Mapper {\n    fun map(v: Validator) = v.ok()\n}\n")
        write(main, "a/endpoint/Validator.kt", "package a.endpoint\n\nclass Validator {\n    fun ok() = true\n}\n")
        write(main, "a/domain/Account.kt", "package a.domain\n\nclass Account\n")
        write(test, "a/endpoint/MapperTest.kt", "package a.endpoint\n\nclass MapperTest {\n    fun t() = Mapper().map(Validator())\n}\n")
    }

    private fun move(preview: Boolean, moveTests: Boolean) = MoveClassRewriter.move(
        sourceRoots = listOf(main, test), className = "a.endpoint.Mapper", newFqcn = "a.domain.Mapper",
        preview = preview, moveTests = moveTests,
    )

    @Test
    fun `without move-tests the test stays and the output says so`() {
        setUp()
        val result = move(preview = true, moveTests = false)

        assertTrue(result.warnings.any { "MapperTest.kt" in it && "move-tests" in it }, "warnings: ${result.warnings}")
        assertTrue(result.movedFiles.size == 1, "${result.movedFiles}")
    }

    @Test
    fun `with move-tests the mirroring test moves to the new package and keeps compiling references`() {
        setUp()
        val result = move(preview = false, moveTests = true)

        assertEquals(null, result.error)
        assertFalse(File(test, "a/endpoint/MapperTest.kt").exists())
        val movedTest = File(test, "a/domain/MapperTest.kt").readText()
        assertTrue(movedTest.startsWith("package a.domain\n"), movedTest)
        assertTrue(movedTest.contains("import a.endpoint.Validator"), movedTest)
        assertFalse(movedTest.contains("import a.domain.Mapper"), movedTest)
        assertTrue(File(main, "a/domain/Mapper.kt").readText().contains("import a.endpoint.Validator"))
        assertEquals(2, result.movedFiles.size)
    }
}
