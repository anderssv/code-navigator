package no.f12.codenavigator.navigation.dsm

import no.f12.codenavigator.navigation.*

import no.f12.codenavigator.navigation.types.ClassName
import no.f12.codenavigator.navigation.dsm.ClassKind
import no.f12.codenavigator.navigation.dsm.ClassTypeCollector
import org.objectweb.asm.Opcodes
import java.io.File
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir

class ClassTypeCollectorTest {

    @TempDir
    lateinit var tempDir: File
    private lateinit var classesDir: File

    @BeforeEach
    fun setUp() {
        classesDir = tempDir.resolve("classes").also { it.mkdirs() }
    }

    @Test
    fun `plain concrete class is classified as CONCRETE`() {
        TestClassWriter.writeClassFile(classesDir, "com/example/Service", "Service.kt")

        val registry = ClassTypeCollector.collect(listOf(classesDir))

        assertEquals(ClassKind.CONCRETE, registry[ClassName("com.example.Service")])
    }

    @Test
    fun `interface is classified as INTERFACE`() {
        TestClassWriter.writeClassFile(
            classesDir, "com/example/Repository", "Repository.kt",
            access = Opcodes.ACC_PUBLIC or Opcodes.ACC_INTERFACE or Opcodes.ACC_ABSTRACT,
        )

        val registry = ClassTypeCollector.collect(listOf(classesDir))

        assertEquals(ClassKind.INTERFACE, registry[ClassName("com.example.Repository")])
    }

    @Test
    fun `abstract class is classified as ABSTRACT`() {
        TestClassWriter.writeClassFile(
            classesDir, "com/example/AbstractService", "AbstractService.kt",
            access = Opcodes.ACC_PUBLIC or Opcodes.ACC_ABSTRACT,
        )

        val registry = ClassTypeCollector.collect(listOf(classesDir))

        assertEquals(ClassKind.ABSTRACT, registry[ClassName("com.example.AbstractService")])
    }

    @Test
    fun `Kotlin data class with copy and componentN is classified as DATA_CLASS`() {
        TestClassWriter.writeClassFile(classesDir, "com/example/Order", "Order.kt") {
            visitMethod(Opcodes.ACC_PUBLIC, "component1", "()Ljava/lang/String;", null, null)
            visitMethod(Opcodes.ACC_PUBLIC, "component2", "()I", null, null)
            visitMethod(Opcodes.ACC_PUBLIC, "copy", "(Ljava/lang/String;I)Lcom/example/Order;", null, null)
            visitMethod(Opcodes.ACC_PUBLIC, "getName", "()Ljava/lang/String;", null, null)
            visitMethod(Opcodes.ACC_PUBLIC, "getAmount", "()I", null, null)
        }

        val registry = ClassTypeCollector.collect(listOf(classesDir))

        assertEquals(ClassKind.DATA_CLASS, registry[ClassName("com.example.Order")])
    }

    @Test
    fun `Java record is classified as RECORD`() {
        TestClassWriter.writeClassFile(
            classesDir, "com/example/Event", "Event.java",
            access = Opcodes.ACC_PUBLIC or Opcodes.ACC_RECORD,
            superName = "java/lang/Record",
        )

        val registry = ClassTypeCollector.collect(listOf(classesDir))

        assertEquals(ClassKind.RECORD, registry[ClassName("com.example.Event")])
    }

    @Test
    fun `class with copy but no componentN is not a data class`() {
        TestClassWriter.writeClassFile(classesDir, "com/example/Builder", "Builder.kt") {
            visitMethod(Opcodes.ACC_PUBLIC, "copy", "()Lcom/example/Builder;", null, null)
            visitMethod(Opcodes.ACC_PUBLIC, "build", "()Ljava/lang/Object;", null, null)
        }

        val registry = ClassTypeCollector.collect(listOf(classesDir))

        assertEquals(ClassKind.CONCRETE, registry[ClassName("com.example.Builder")])
    }

    @Test
    fun `empty directory produces empty registry`() {
        val registry = ClassTypeCollector.collect(listOf(classesDir))

        assertEquals(0, registry.size)
    }

    @Test
    fun `multiple classes across directories are all collected`() {
        val dir2 = tempDir.resolve("classes2").also { it.mkdirs() }
        TestClassWriter.writeClassFile(classesDir, "com/example/A", "A.kt")
        TestClassWriter.writeClassFile(dir2, "com/example/B", "B.kt",
            access = Opcodes.ACC_PUBLIC or Opcodes.ACC_INTERFACE or Opcodes.ACC_ABSTRACT)

        val registry = ClassTypeCollector.collect(listOf(classesDir, dir2))

        assertEquals(ClassKind.CONCRETE, registry[ClassName("com.example.A")])
        assertEquals(ClassKind.INTERFACE, registry[ClassName("com.example.B")])
    }

    @Test
    fun `abstract interface is classified as INTERFACE not ABSTRACT`() {
        TestClassWriter.writeClassFile(
            classesDir, "com/example/Repo", "Repo.kt",
            access = Opcodes.ACC_PUBLIC or Opcodes.ACC_INTERFACE or Opcodes.ACC_ABSTRACT,
        )

        val registry = ClassTypeCollector.collect(listOf(classesDir))

        assertEquals(ClassKind.INTERFACE, registry[ClassName("com.example.Repo")])
    }

    @Test
    fun `concrete class with Embeddable annotation is classified as ANNOTATED_MODEL`() {
        TestClassWriter.writeClassFile(classesDir, "com/example/Address", "Address.java") {
            visitAnnotation("Ljakarta/persistence/Embeddable;", true)
        }

        val registry = ClassTypeCollector.collect(
            listOf(classesDir),
            modelAnnotations = setOf("jakarta.persistence.Embeddable"),
        )

        assertEquals(ClassKind.ANNOTATED_MODEL, registry[ClassName("com.example.Address")])
    }

    @Test
    fun `concrete class with javax Entity annotation is classified as ANNOTATED_MODEL`() {
        TestClassWriter.writeClassFile(classesDir, "com/example/LegacyOwner", "LegacyOwner.java") {
            visitAnnotation("Ljavax/persistence/Entity;", true)
        }

        val registry = ClassTypeCollector.collect(
            listOf(classesDir),
            modelAnnotations = setOf("javax.persistence.Entity"),
        )

        assertEquals(ClassKind.ANNOTATED_MODEL, registry[ClassName("com.example.LegacyOwner")])
    }

    @Test
    fun `model annotation not in provided set is ignored`() {
        TestClassWriter.writeClassFile(classesDir, "com/example/Service", "Service.java") {
            visitAnnotation("Lorg/springframework/stereotype/Service;", true)
        }

        val registry = ClassTypeCollector.collect(
            listOf(classesDir),
            modelAnnotations = setOf("jakarta.persistence.Entity"),
        )

        assertEquals(ClassKind.CONCRETE, registry[ClassName("com.example.Service")])
    }

    @Test
    fun `concrete class with Entity annotation is classified as ANNOTATED_MODEL`() {
        TestClassWriter.writeClassFile(classesDir, "com/example/Owner", "Owner.java") {
            visitAnnotation("Ljakarta/persistence/Entity;", true)
        }

        val registry = ClassTypeCollector.collect(
            listOf(classesDir),
            modelAnnotations = setOf("jakarta.persistence.Entity"),
        )

        assertEquals(ClassKind.ANNOTATED_MODEL, registry[ClassName("com.example.Owner")])
    }

    @Test
    fun `abstract class with MappedSuperclass annotation is classified as ANNOTATED_MODEL`() {
        TestClassWriter.writeClassFile(
            classesDir, "com/example/BaseEntity", "BaseEntity.java",
            access = Opcodes.ACC_PUBLIC or Opcodes.ACC_ABSTRACT,
        ) {
            visitAnnotation("Ljakarta/persistence/MappedSuperclass;", true)
        }

        val registry = ClassTypeCollector.collect(
            listOf(classesDir),
            modelAnnotations = setOf("jakarta.persistence.MappedSuperclass"),
        )

        assertEquals(ClassKind.ANNOTATED_MODEL, registry[ClassName("com.example.BaseEntity")])
    }

    @Test
    fun `resolveFromClasspath classifies interface from JAR`() {
        val jarFile = createJarWithClass(
            tempDir, "lib.jar",
            "org/framework/Repository",
            access = Opcodes.ACC_PUBLIC or Opcodes.ACC_INTERFACE or Opcodes.ACC_ABSTRACT,
        )

        val result = ClassTypeCollector.resolveFromClasspath(
            setOf(ClassName("org.framework.Repository")),
            listOf(jarFile.toPath()),
        )

        assertEquals(ClassKind.INTERFACE, result[ClassName("org.framework.Repository")])
    }

    @Test
    fun `resolveFromClasspath classifies concrete class from JAR`() {
        val jarFile = createJarWithClass(tempDir, "lib.jar", "org/framework/ServiceImpl")

        val result = ClassTypeCollector.resolveFromClasspath(
            setOf(ClassName("org.framework.ServiceImpl")),
            listOf(jarFile.toPath()),
        )

        assertEquals(ClassKind.CONCRETE, result[ClassName("org.framework.ServiceImpl")])
    }

    @Test
    fun `resolveFromClasspath returns empty for classes not in classpath`() {
        val jarFile = createJarWithClass(tempDir, "lib.jar", "org/framework/Other")

        val result = ClassTypeCollector.resolveFromClasspath(
            setOf(ClassName("org.framework.Missing")),
            listOf(jarFile.toPath()),
        )

        assertTrue(result.isEmpty())
    }

    private fun createJarWithClass(
        dir: File,
        jarName: String,
        className: String,
        access: Int = Opcodes.ACC_PUBLIC,
    ): File {
        val jarFile = File(dir, jarName)
        val writer = org.objectweb.asm.ClassWriter(0)
        writer.visit(Opcodes.V17, access, className, null, "java/lang/Object", null)
        writer.visitEnd()

        JarOutputStream(jarFile.outputStream()).use { jos ->
            jos.putNextEntry(JarEntry("$className.class"))
            jos.write(writer.toByteArray())
            jos.closeEntry()
        }
        return jarFile
    }
}
