package no.f12.codenavigator.navigation

import no.f12.codenavigator.navigation.callgraph.MethodRef
import no.f12.codenavigator.navigation.annotation.AnnotationExtractor
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

        assertEquals(setOf(AnnotationName("org.springframework.web.bind.annotation.RestController")), result.classAnnotations)
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
        assertEquals(setOf(AnnotationName("org.springframework.scheduling.annotation.Scheduled")), result.methodAnnotations[methodRef])
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
            setOf(AnnotationName("org.springframework.web.bind.annotation.RestController"), AnnotationName("org.springframework.stereotype.Component")),
            result.classAnnotations,
        )
        val methodRef = MethodRef(ClassName("com.example.MyController"), "handle")
        assertEquals(
            setOf(AnnotationName("org.springframework.web.bind.annotation.GetMapping"), AnnotationName("io.micrometer.core.annotation.Timed")),
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
    fun `extracts class-level annotation parameters`() {
        val classFile = TestClassWriter.writeClassFile(
            tempDir.toFile(), "com/example/MyController", "MyController.kt",
        ) {
            val av = visitAnnotation("Lorg/springframework/web/bind/annotation/RequestMapping;", true)
            av.visit("value", "/api")
            av.visitEnd()
        }

        val result = AnnotationExtractor.extract(classFile)

        val annotation = AnnotationName("org.springframework.web.bind.annotation.RequestMapping")
        assertEquals(setOf(annotation), result.classAnnotations)
        assertEquals(mapOf("value" to "/api"), result.classAnnotationParameters[annotation])
    }

    @Test
    fun `extracts method-level annotation parameters`() {
        val classFile = TestClassWriter.writeClassFile(
            tempDir.toFile(), "com/example/MyController", "MyController.kt",
        ) {
            val mv = visitMethod(Opcodes.ACC_PUBLIC, "handle", "()V", null, null)
            val av = mv.visitAnnotation("Lorg/springframework/web/bind/annotation/GetMapping;", true)
            av.visit("value", "/users")
            av.visitEnd()
            mv.visitEnd()
        }

        val result = AnnotationExtractor.extract(classFile)

        val methodRef = MethodRef(ClassName("com.example.MyController"), "handle")
        val annotation = AnnotationName("org.springframework.web.bind.annotation.GetMapping")
        assertEquals(setOf(annotation), result.methodAnnotations[methodRef])
        assertEquals(mapOf("value" to "/users"), result.methodAnnotationParameters[methodRef]?.get(annotation))
    }

    @Test
    fun `annotations without parameters have empty parameter map`() {
        val classFile = TestClassWriter.writeClassFile(
            tempDir.toFile(), "com/example/MyController", "MyController.kt",
        ) {
            visitAnnotation("Lorg/springframework/web/bind/annotation/RestController;", true)?.visitEnd()
        }

        val result = AnnotationExtractor.extract(classFile)

        val annotation = AnnotationName("org.springframework.web.bind.annotation.RestController")
        assertEquals(emptyMap(), result.classAnnotationParameters[annotation])
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

        val result = AnnotationExtractor.scanAll(listOf(dir))

        assertEquals(
            setOf(AnnotationName("org.springframework.web.bind.annotation.RestController")),
            result.classAnnotations[ClassName("com.example.Controller")],
        )
        assertEquals(
            setOf(AnnotationName("org.springframework.stereotype.Service")),
            result.classAnnotations[ClassName("com.example.Service")],
        )
        assertTrue(ClassName("com.example.Plain") !in result.classAnnotations)

        val handleRef = MethodRef(ClassName("com.example.Controller"), "handle")
        assertEquals(
            setOf(AnnotationName("org.springframework.web.bind.annotation.GetMapping")),
            result.methodAnnotations[handleRef],
        )
    }

    @Test
    fun `repeatable annotation container on method is unwrapped`() {
        val classFile = TestClassWriter.writeClassFile(
            tempDir.toFile(), "com/example/WithJoins", "WithJoins.kt",
        ) {
            val mv = visitMethod(Opcodes.ACC_PUBLIC, "findAll", "()V", null, null)
            val container = mv.visitAnnotation("Lnet/kaczmarzyk/spring/data/jpa/web/annotation/RepeatedJoin;", true)
            val valueArray = container?.visitArray("value")
            val join1 = valueArray?.visitAnnotation(null, "Lnet/kaczmarzyk/spring/data/jpa/web/annotation/Join;")
            join1?.visit("path", "author")
            join1?.visitEnd()
            val join2 = valueArray?.visitAnnotation(null, "Lnet/kaczmarzyk/spring/data/jpa/web/annotation/Join;")
            join2?.visit("path", "tags")
            join2?.visitEnd()
            valueArray?.visitEnd()
            container?.visitEnd()
            mv.visitEnd()
        }

        val result = AnnotationExtractor.extract(classFile)

        val methodRef = MethodRef(ClassName("com.example.WithJoins"), "findAll")
        val joinName = AnnotationName("net.kaczmarzyk.spring.data.jpa.web.annotation.Join")
        assertTrue(result.methodAnnotations[methodRef]!!.contains(joinName))
        assertTrue(!result.methodAnnotations[methodRef]!!.contains(AnnotationName("net.kaczmarzyk.spring.data.jpa.web.annotation.RepeatedJoin")))
        // Map can only hold one set of params per name; the last @Join's params win
        assertEquals(mapOf("path" to "tags"), result.methodAnnotationParameters[methodRef]?.get(joinName))
    }

    @Test
    fun `repeatable annotation container on class is unwrapped`() {
        val classFile = TestClassWriter.writeClassFile(
            tempDir.toFile(), "com/example/WithSources", "WithSources.kt",
        ) {
            val container = visitAnnotation("Lorg/springframework/context/annotation/PropertySources;", true)
            val valueArray = container?.visitArray("value")
            val ps1 = valueArray?.visitAnnotation(null, "Lorg/springframework/context/annotation/PropertySource;")
            ps1?.visit("value", "classpath:app.properties")
            ps1?.visitEnd()
            val ps2 = valueArray?.visitAnnotation(null, "Lorg/springframework/context/annotation/PropertySource;")
            ps2?.visit("value", "classpath:db.properties")
            ps2?.visitEnd()
            valueArray?.visitEnd()
            container?.visitEnd()
        }

        val result = AnnotationExtractor.extract(classFile)

        val sourceName = AnnotationName("org.springframework.context.annotation.PropertySource")
        assertTrue(result.classAnnotations.contains(sourceName))
        assertTrue(!result.classAnnotations.contains(AnnotationName("org.springframework.context.annotation.PropertySources")))
        // Map can only hold one set of params per name; the last @PropertySource's params win
        assertEquals(mapOf("value" to "classpath:db.properties"), result.classAnnotationParameters[sourceName])
    }

    @Test
    fun `filters out Kotlin internal annotations from class-level annotations`() {
        val classFile = TestClassWriter.writeClassFile(
            tempDir.toFile(), "com/example/KotlinService", "KotlinService.kt",
        ) {
            visitAnnotation("Lkotlin/Metadata;", true)?.visitEnd()
            visitAnnotation("Lkotlin/jvm/internal/SourceDebugExtension;", true)?.visitEnd()
            visitAnnotation("Lorg/springframework/stereotype/Service;", true)?.visitEnd()
        }

        val result = AnnotationExtractor.extract(classFile)

        assertEquals(setOf(AnnotationName("org.springframework.stereotype.Service")), result.classAnnotations)
    }

    @Test
    fun `filters out Kotlin internal annotations from method-level annotations`() {
        val classFile = TestClassWriter.writeClassFile(
            tempDir.toFile(), "com/example/SuspendService", "SuspendService.kt",
        ) {
            val mv = visitMethod(Opcodes.ACC_PUBLIC, "doWork", "()V", null, null)
            mv.visitAnnotation("Lkotlin/coroutines/jvm/internal/DebugMetadata;", true)?.visitEnd()
            mv.visitAnnotation("Lorg/springframework/scheduling/annotation/Scheduled;", true)?.visitEnd()
            mv.visitEnd()
        }

        val result = AnnotationExtractor.extract(classFile)

        val methodRef = MethodRef(ClassName("com.example.SuspendService"), "doWork")
        assertEquals(
            setOf(AnnotationName("org.springframework.scheduling.annotation.Scheduled")),
            result.methodAnnotations[methodRef],
        )
    }

    @Test
    fun `scanAll excludes Kotlin internal annotations from aggregated results`() {
        val dir = tempDir.toFile()

        TestClassWriter.writeClassFile(dir, "com/example/KotlinClass", "KotlinClass.kt") {
            visitAnnotation("Lkotlin/Metadata;", true)?.visitEnd()
            visitAnnotation("Lorg/springframework/stereotype/Component;", true)?.visitEnd()
        }

        val result = AnnotationExtractor.scanAll(listOf(dir))

        assertEquals(
            setOf(AnnotationName("org.springframework.stereotype.Component")),
            result.classAnnotations[ClassName("com.example.KotlinClass")],
        )
    }
}
