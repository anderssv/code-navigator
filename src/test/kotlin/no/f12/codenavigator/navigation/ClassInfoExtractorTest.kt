package no.f12.codenavigator.navigation

import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ClassInfoExtractorTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `reads class name from a compiled class file`() {
        val classFile = TestClassWriter.writeClassFile(tempDir.toFile(), "com/example/MyService", "MyService.kt")

        val result = ClassInfoExtractor.extract(classFile)

        assertEquals("com.example.MyService", result.className.value)
    }

    @Test
    fun `reads source file attribute from a compiled class file`() {
        val classFile = TestClassWriter.writeClassFile(tempDir.toFile(), "com/example/MyService", "MyService.kt")

        val result = ClassInfoExtractor.extract(classFile)

        assertEquals("MyService.kt", result.sourceFileName)
    }

    @Test
    fun `reconstructs source path from package and source file name`() {
        val classFile = TestClassWriter.writeClassFile(tempDir.toFile(), "com/example/deep/MyService", "MyService.kt")

        val result = ClassInfoExtractor.extract(classFile)

        assertEquals("com/example/deep/MyService.kt", result.reconstructedSourcePath)
    }

    @Test
    fun `handles class file without source file attribute`() {
        val classFile = TestClassWriter.writeClassFile(tempDir.toFile(), "com/example/Generated", null)

        val result = ClassInfoExtractor.extract(classFile)

        assertEquals("com.example.Generated", result.className.value)
        assertEquals("<unknown>", result.sourceFileName)
        assertEquals("<unknown>", result.reconstructedSourcePath)
    }

    @Test
    fun `anonymous inner classes are not user-defined`() {
        val classFile = TestClassWriter.writeClassFile(tempDir.toFile(), "com/example/Foo\$1", "Foo.kt")

        val result = ClassInfoExtractor.extract(classFile)

        assertTrue(!result.isUserDefinedClass)
    }

    @Test
    fun `lambda generated classes are not user-defined`() {
        val classFile = TestClassWriter.writeClassFile(tempDir.toFile(), "com/example/Foo\$lambda\$1", "Foo.kt")

        val result = ClassInfoExtractor.extract(classFile)

        assertTrue(!result.isUserDefinedClass)
    }

    @Test
    fun `named inner classes are user-defined`() {
        val classFile = TestClassWriter.writeClassFile(tempDir.toFile(), "com/example/Foo\$Bar", "Foo.kt")

        val result = ClassInfoExtractor.extract(classFile)

        assertTrue(result.isUserDefinedClass)
        assertEquals("com.example.Foo.Bar", result.className.value)
    }

    @Test
    fun `regular top-level class is user-defined`() {
        val classFile = TestClassWriter.writeClassFile(tempDir.toFile(), "com/example/MyService", "MyService.kt")

        val result = ClassInfoExtractor.extract(classFile)

        assertTrue(result.isUserDefinedClass)
    }
}
