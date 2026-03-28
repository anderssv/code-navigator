package no.f12.codenavigator.navigation

import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.Opcodes
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AnnotationExtractorTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `extracts class-level annotation as FQN`() {
        val classFile = TestClassWriter.writeClassFile(
            tempDir.toFile(), "com/example/MyController", "MyController.kt",
        ) {
            visitAnnotation("Lorg/springframework/web/bind/annotation/RestController;", true)
        }

        val result = AnnotationExtractor.extract(classFile)

        assertEquals(setOf("org.springframework.web.bind.annotation.RestController"), result.classAnnotations)
    }

    @Test
    fun `extracts method-level annotation as FQN`() {
        val classFile = TestClassWriter.writeClassFile(
            tempDir.toFile(), "com/example/MyService", "MyService.kt",
        ) {
            val mv = visitMethod(Opcodes.ACC_PUBLIC, "process", "()V", null, null)
            mv.visitAnnotation("Lorg/springframework/scheduling/annotation/Scheduled;", true)
            mv.visitEnd()
        }

        val result = AnnotationExtractor.extract(classFile)

        val methodRef = MethodRef(ClassName("com.example.MyService"), "process")
        assertEquals(setOf("org.springframework.scheduling.annotation.Scheduled"), result.methodAnnotations[methodRef])
    }

    @Test
    fun `extracts multiple annotations on class and methods`() {
        val classFile = TestClassWriter.writeClassFile(
            tempDir.toFile(), "com/example/MyController", "MyController.kt",
        ) {
            visitAnnotation("Lorg/springframework/web/bind/annotation/RestController;", true)
            visitAnnotation("Lorg/springframework/stereotype/Component;", true)

            val mv = visitMethod(Opcodes.ACC_PUBLIC, "handle", "()V", null, null)
            mv.visitAnnotation("Lorg/springframework/web/bind/annotation/GetMapping;", true)
            mv.visitAnnotation("Lio/micrometer/core/annotation/Timed;", true)
            mv.visitEnd()
        }

        val result = AnnotationExtractor.extract(classFile)

        assertEquals(
            setOf("org.springframework.web.bind.annotation.RestController", "org.springframework.stereotype.Component"),
            result.classAnnotations,
        )
        val methodRef = MethodRef(ClassName("com.example.MyController"), "handle")
        assertEquals(
            setOf("org.springframework.web.bind.annotation.GetMapping", "io.micrometer.core.annotation.Timed"),
            result.methodAnnotations[methodRef],
        )
    }

    @Test
    fun `returns empty sets for class with no annotations`() {
        val classFile = TestClassWriter.writeClassFile(
            tempDir.toFile(), "com/example/Plain", "Plain.kt",
        )

        val result = AnnotationExtractor.extract(classFile)

        assertTrue(result.classAnnotations.isEmpty())
        assertTrue(result.methodAnnotations.isEmpty())
    }

    @Test
    fun `skips constructors and synthetic methods`() {
        val classFile = TestClassWriter.writeClassFile(
            tempDir.toFile(), "com/example/MyService", "MyService.kt",
        ) {
            val init = visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null)
            init.visitAnnotation("Ljakarta/inject/Inject;", true)
            init.visitCode()
            init.visitVarInsn(Opcodes.ALOAD, 0)
            init.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
            init.visitInsn(Opcodes.RETURN)
            init.visitMaxs(1, 1)
            init.visitEnd()
        }

        val result = AnnotationExtractor.extract(classFile)

        assertTrue(result.methodAnnotations.isEmpty(), "Constructor annotations should be skipped")
    }

    @Test
    fun `scanAll builds class and method annotation maps from directory`() {
        val dir = tempDir.toFile()

        TestClassWriter.writeClassFile(dir, "com/example/Controller", "Controller.kt") {
            visitAnnotation("Lorg/springframework/web/bind/annotation/RestController;", true)
            val mv = visitMethod(Opcodes.ACC_PUBLIC, "handle", "()V", null, null)
            mv.visitAnnotation("Lorg/springframework/web/bind/annotation/GetMapping;", true)
            mv.visitEnd()
        }
        TestClassWriter.writeClassFile(dir, "com/example/Service", "Service.kt") {
            visitAnnotation("Lorg/springframework/stereotype/Service;", true)
        }
        TestClassWriter.writeClassFile(dir, "com/example/Plain", "Plain.kt")

        val (classAnnotations, methodAnnotations) = AnnotationExtractor.scanAll(listOf(dir))

        assertEquals(
            setOf("org.springframework.web.bind.annotation.RestController"),
            classAnnotations[ClassName("com.example.Controller")],
        )
        assertEquals(
            setOf("org.springframework.stereotype.Service"),
            classAnnotations[ClassName("com.example.Service")],
        )
        assertTrue(ClassName("com.example.Plain") !in classAnnotations)

        val handleRef = MethodRef(ClassName("com.example.Controller"), "handle")
        assertEquals(
            setOf("org.springframework.web.bind.annotation.GetMapping"),
            methodAnnotations[handleRef],
        )
    }
}
