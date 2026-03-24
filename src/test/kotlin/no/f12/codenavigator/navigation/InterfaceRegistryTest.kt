package no.f12.codenavigator.navigation

import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InterfaceRegistryTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `finds implementors of an interface`() {
        writeClassFile("com/example/Repository", "Repository.kt")
        writeClassFile(
            "com/example/UserRepository",
            "UserRepository.kt",
            interfaces = arrayOf("com/example/Repository"),
        )

        val registry = InterfaceRegistry.build(listOf(tempDir.toFile())).data

        val implementors = registry.implementorsOf("com.example.Repository")
        assertEquals(1, implementors.size)
        assertEquals("com.example.UserRepository", implementors.first().className)
        assertEquals("UserRepository.kt", implementors.first().sourceFile)
    }

    // [TEST-DONE] Finds implementors of an interface
    @Test
    fun `returns empty list for interface with no implementors`() {
        writeClassFile("com/example/Repository", "Repository.kt")

        val registry = InterfaceRegistry.build(listOf(tempDir.toFile())).data

        assertTrue(registry.implementorsOf("com.example.Repository").isEmpty())
    }

    // [TEST-DONE] Returns empty set for interface with no implementors

    @Test
    fun `finds multiple implementors of the same interface`() {
        writeClassFile(
            "com/example/UserRepo",
            "UserRepo.kt",
            interfaces = arrayOf("com/example/Repository"),
        )
        writeClassFile(
            "com/example/OrderRepo",
            "OrderRepo.kt",
            interfaces = arrayOf("com/example/Repository"),
        )

        val registry = InterfaceRegistry.build(listOf(tempDir.toFile())).data

        val names = registry.implementorsOf("com.example.Repository").map { it.className }
        assertEquals(listOf("com.example.OrderRepo", "com.example.UserRepo"), names)
    }

    // [TEST-DONE] Finds multiple implementors of the same interface

    @Test
    fun `class implementing multiple interfaces appears under each`() {
        writeClassFile(
            "com/example/FullRepo",
            "FullRepo.kt",
            interfaces = arrayOf("com/example/Readable", "com/example/Writable"),
        )

        val registry = InterfaceRegistry.build(listOf(tempDir.toFile())).data

        assertEquals(1, registry.implementorsOf("com.example.Readable").size)
        assertEquals(1, registry.implementorsOf("com.example.Writable").size)
        assertEquals("com.example.FullRepo", registry.implementorsOf("com.example.Readable").first().className)
    }

    // [TEST-DONE] Class implementing multiple interfaces appears under each

    @Test
    fun `findInterfaces matches pattern case-insensitively`() {
        writeClassFile(
            "com/example/UserRepo",
            "UserRepo.kt",
            interfaces = arrayOf("com/example/Repository"),
        )
        writeClassFile(
            "com/example/EventHandler",
            "EventHandler.kt",
            interfaces = arrayOf("com/example/Handler"),
        )

        val registry = InterfaceRegistry.build(listOf(tempDir.toFile())).data

        val matches = registry.findInterfaces("repo")
        assertEquals(listOf("com.example.Repository"), matches)
    }

    // [TEST-DONE] findInterfaces matches pattern case-insensitively

    @Test
    fun `skips synthetic and lambda classes`() {
        writeClassFile(
            "com/example/Service\$1",
            "Service.kt",
            interfaces = arrayOf("com/example/Callback"),
        )
        writeClassFile(
            "com/example/Service\$lambda\$0",
            "Service.kt",
            interfaces = arrayOf("com/example/Callback"),
        )
        writeClassFile(
            "com/example/RealImpl",
            "RealImpl.kt",
            interfaces = arrayOf("com/example/Callback"),
        )

        val registry = InterfaceRegistry.build(listOf(tempDir.toFile())).data

        val names = registry.implementorsOf("com.example.Callback").map { it.className }
        assertEquals(listOf("com.example.RealImpl"), names)
    }

    // [TEST-DONE] Skips synthetic and lambda classes

    @Test
    fun `returns implementors sorted by class name`() {
        writeClassFile("com/example/Zebra", "Zebra.kt", interfaces = arrayOf("com/example/Animal"))
        writeClassFile("com/example/Apple", "Apple.kt", interfaces = arrayOf("com/example/Animal"))
        writeClassFile("com/example/Mango", "Mango.kt", interfaces = arrayOf("com/example/Animal"))

        val registry = InterfaceRegistry.build(listOf(tempDir.toFile())).data

        val names = registry.implementorsOf("com.example.Animal").map { it.className }
        assertEquals(listOf("com.example.Apple", "com.example.Mango", "com.example.Zebra"), names)
    }

    // [TEST-DONE] Returns implementors sorted by class name

    @Test
    fun `build merges implementors from multiple class directories`() {
        val mainDir = tempDir.resolve("main").toFile().also { it.mkdirs() }
        val testDir = tempDir.resolve("test").toFile().also { it.mkdirs() }

        writeClassFileTo(
            mainDir,
            "com/example/RealRepo",
            "RealRepo.kt",
            interfaces = arrayOf("com/example/Repository"),
        )
        writeClassFileTo(
            testDir,
            "com/example/FakeRepo",
            "FakeRepo.kt",
            interfaces = arrayOf("com/example/Repository"),
        )

        val registry = InterfaceRegistry.build(listOf(mainDir, testDir)).data

        val names = registry.implementorsOf("com.example.Repository").map { it.className }
        assertEquals(listOf("com.example.FakeRepo", "com.example.RealRepo"), names)
    }

    private fun writeClassFile(
        className: String,
        sourceFile: String?,
        superName: String = "java/lang/Object",
        interfaces: Array<String>? = null,
    ): File {
        val writer = ClassWriter(0)
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, className, null, superName, interfaces)
        if (sourceFile != null) {
            writer.visitSource(sourceFile, null)
        }
        writer.visitEnd()

        val packageDir = className.substringBeforeLast("/", "")
        val simpleFileName = className.substringAfterLast("/") + ".class"
        val dir = if (packageDir.isNotEmpty()) {
            tempDir.resolve(packageDir).toFile().also { it.mkdirs() }
        } else {
            tempDir.toFile()
        }
        val file = File(dir, simpleFileName)
        file.writeBytes(writer.toByteArray())
        return file
    }

    private fun writeClassFileTo(
        baseDir: File,
        className: String,
        sourceFile: String?,
        superName: String = "java/lang/Object",
        interfaces: Array<String>? = null,
    ): File {
        val writer = ClassWriter(0)
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, className, null, superName, interfaces)
        if (sourceFile != null) {
            writer.visitSource(sourceFile, null)
        }
        writer.visitEnd()

        val packageDir = className.substringBeforeLast("/", "")
        val simpleFileName = className.substringAfterLast("/") + ".class"
        val dir = if (packageDir.isNotEmpty()) {
            baseDir.resolve(packageDir).also { it.mkdirs() }
        } else {
            baseDir
        }
        val file = File(dir, simpleFileName)
        file.writeBytes(writer.toByteArray())
        return file
    }
}
