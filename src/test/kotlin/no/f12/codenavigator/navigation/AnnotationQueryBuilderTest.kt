package no.f12.codenavigator.navigation

import no.f12.codenavigator.navigation.annotation.AnnotationQueryBuilder
import org.objectweb.asm.Opcodes
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AnnotationQueryBuilderTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `finds classes with matching class-level annotation`() {
        val dir = tempDir.toFile()
        TestClassWriter.writeClassFile(dir, "com/example/MyController", "MyController.kt") {
            visitAnnotation("Lorg/springframework/web/bind/annotation/RestController;", true)
        }
        TestClassWriter.writeClassFile(dir, "com/example/MyService", "MyService.kt") {
            visitAnnotation("Lorg/springframework/stereotype/Service;", true)
        }

        val result = AnnotationQueryBuilder.query(listOf(dir), pattern = "RestController", methods = false)

        assertEquals(1, result.size)
        assertEquals("com.example.MyController", result[0].className.value)
        assertEquals(
            setOf(AnnotationName("org.springframework.web.bind.annotation.RestController")),
            result[0].classAnnotations,
        )
    }

    @Test
    fun `finds methods with matching annotation when methods=true`() {
        val dir = tempDir.toFile()
        TestClassWriter.writeClassFile(dir, "com/example/MyController", "MyController.kt") {
            val mv = visitMethod(Opcodes.ACC_PUBLIC, "handle", "()V", null, null)
            mv.visitAnnotation("Lorg/springframework/web/bind/annotation/GetMapping;", true)
            mv.visitEnd()
        }

        val result = AnnotationQueryBuilder.query(listOf(dir), pattern = "GetMapping", methods = true)

        assertEquals(1, result.size)
        assertEquals("com.example.MyController", result[0].className.value)
        assertEquals(1, result[0].matchedMethods.size)
        assertEquals("handle", result[0].matchedMethods[0].method.methodName)
        assertEquals(
            setOf(AnnotationName("org.springframework.web.bind.annotation.GetMapping")),
            result[0].matchedMethods[0].annotations,
        )
    }

    @Test
    fun `pattern is case-insensitive regex`() {
        val dir = tempDir.toFile()
        TestClassWriter.writeClassFile(dir, "com/example/MyService", "MyService.kt") {
            visitAnnotation("Lorg/springframework/stereotype/Service;", true)
        }

        val result = AnnotationQueryBuilder.query(listOf(dir), pattern = "service", methods = false)

        assertEquals(1, result.size)
        assertEquals("com.example.MyService", result[0].className.value)
    }

    @Test
    fun `returns empty result when no annotations match`() {
        val dir = tempDir.toFile()
        TestClassWriter.writeClassFile(dir, "com/example/Plain", "Plain.kt")

        val result = AnnotationQueryBuilder.query(listOf(dir), pattern = "Service", methods = false)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `excludes method-only matches when methods=false`() {
        val dir = tempDir.toFile()
        TestClassWriter.writeClassFile(dir, "com/example/MyController", "MyController.kt") {
            val mv = visitMethod(Opcodes.ACC_PUBLIC, "handle", "()V", null, null)
            mv.visitAnnotation("Lorg/springframework/web/bind/annotation/GetMapping;", true)
            mv.visitEnd()
        }

        val result = AnnotationQueryBuilder.query(listOf(dir), pattern = "GetMapping", methods = false)

        assertTrue(result.isEmpty(), "Method-only matches should be excluded when methods=false")
    }

    @Test
    fun `matches multiple classes with same annotation`() {
        val dir = tempDir.toFile()
        TestClassWriter.writeClassFile(dir, "com/example/ControllerA", "ControllerA.kt") {
            visitAnnotation("Lorg/springframework/web/bind/annotation/RestController;", true)
        }
        TestClassWriter.writeClassFile(dir, "com/example/ControllerB", "ControllerB.kt") {
            visitAnnotation("Lorg/springframework/web/bind/annotation/RestController;", true)
        }

        val result = AnnotationQueryBuilder.query(listOf(dir), pattern = "RestController", methods = false)

        assertEquals(2, result.size)
        assertEquals("com.example.ControllerA", result[0].className.value)
        assertEquals("com.example.ControllerB", result[1].className.value)
    }

    @Test
    fun `includes source file in result`() {
        val dir = tempDir.toFile()
        TestClassWriter.writeClassFile(dir, "com/example/MyController", "MyController.kt") {
            visitAnnotation("Lorg/springframework/web/bind/annotation/RestController;", true)
        }

        val result = AnnotationQueryBuilder.query(listOf(dir), pattern = "RestController", methods = false)

        assertEquals("MyController.kt", result[0].sourceFile)
    }

    @Test
    fun `includes all class annotations on matching class not just the matched one`() {
        val dir = tempDir.toFile()
        TestClassWriter.writeClassFile(dir, "com/example/MyController", "MyController.kt") {
            visitAnnotation("Lorg/springframework/web/bind/annotation/RestController;", true)
            visitAnnotation("Lorg/springframework/web/bind/annotation/RequestMapping;", true)
        }

        val result = AnnotationQueryBuilder.query(listOf(dir), pattern = "RestController", methods = false)

        assertEquals(
            setOf(
                AnnotationName("org.springframework.web.bind.annotation.RestController"),
                AnnotationName("org.springframework.web.bind.annotation.RequestMapping"),
            ),
            result[0].classAnnotations,
        )
    }

    @Test
    fun `method match returns class even if class has no matching class-level annotation`() {
        val dir = tempDir.toFile()
        TestClassWriter.writeClassFile(dir, "com/example/Plain", "Plain.kt") {
            val mv = visitMethod(Opcodes.ACC_PUBLIC, "scheduled", "()V", null, null)
            mv.visitAnnotation("Lorg/springframework/scheduling/annotation/Scheduled;", true)
            mv.visitEnd()
        }

        val result = AnnotationQueryBuilder.query(listOf(dir), pattern = "Scheduled", methods = true)

        assertEquals(1, result.size)
        assertEquals("com.example.Plain", result[0].className.value)
        assertTrue(result[0].classAnnotations.isEmpty())
        assertEquals(1, result[0].matchedMethods.size)
        assertEquals("scheduled", result[0].matchedMethods[0].method.methodName)
    }
}
