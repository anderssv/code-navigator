package no.f12.codenavigator.navigation

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
        TestClassWriter.writeClassFile(tempDir.toFile(), "com/example/Repository", "Repository.kt")
        TestClassWriter.writeClassFile(
            tempDir.toFile(), "com/example/UserRepository", "UserRepository.kt",
            interfaces = arrayOf("com/example/Repository"),
        )

        val registry = InterfaceRegistry.build(listOf(tempDir.toFile())).data

        val implementors = registry.implementorsOf(ClassName("com.example.Repository"))
        assertEquals(1, implementors.size)
        assertEquals("com.example.UserRepository", implementors.first().className.value)
        assertEquals("UserRepository.kt", implementors.first().sourceFile)
    }

    // [TEST-DONE] Finds implementors of an interface
    @Test
    fun `returns empty list for interface with no implementors`() {
        TestClassWriter.writeClassFile(tempDir.toFile(), "com/example/Repository", "Repository.kt")

        val registry = InterfaceRegistry.build(listOf(tempDir.toFile())).data

        assertTrue(registry.implementorsOf(ClassName("com.example.Repository")).isEmpty())
    }

    // [TEST-DONE] Returns empty set for interface with no implementors

    @Test
    fun `finds multiple implementors of the same interface`() {
        TestClassWriter.writeClassFile(
            tempDir.toFile(), "com/example/UserRepo", "UserRepo.kt",
            interfaces = arrayOf("com/example/Repository"),
        )
        TestClassWriter.writeClassFile(
            tempDir.toFile(), "com/example/OrderRepo", "OrderRepo.kt",
            interfaces = arrayOf("com/example/Repository"),
        )

        val registry = InterfaceRegistry.build(listOf(tempDir.toFile())).data

        val names = registry.implementorsOf(ClassName("com.example.Repository")).map { it.className.value }
        assertEquals(listOf("com.example.OrderRepo", "com.example.UserRepo"), names)
    }

    // [TEST-DONE] Finds multiple implementors of the same interface

    @Test
    fun `class implementing multiple interfaces appears under each`() {
        TestClassWriter.writeClassFile(
            tempDir.toFile(), "com/example/FullRepo", "FullRepo.kt",
            interfaces = arrayOf("com/example/Readable", "com/example/Writable"),
        )

        val registry = InterfaceRegistry.build(listOf(tempDir.toFile())).data

        assertEquals(1, registry.implementorsOf(ClassName("com.example.Readable")).size)
        assertEquals(1, registry.implementorsOf(ClassName("com.example.Writable")).size)
        assertEquals("com.example.FullRepo", registry.implementorsOf(ClassName("com.example.Readable")).first().className.value)
    }

    // [TEST-DONE] Class implementing multiple interfaces appears under each

    @Test
    fun `findInterfaces matches pattern case-insensitively`() {
        TestClassWriter.writeClassFile(
            tempDir.toFile(), "com/example/UserRepo", "UserRepo.kt",
            interfaces = arrayOf("com/example/Repository"),
        )
        TestClassWriter.writeClassFile(
            tempDir.toFile(), "com/example/EventHandler", "EventHandler.kt",
            interfaces = arrayOf("com/example/Handler"),
        )

        val registry = InterfaceRegistry.build(listOf(tempDir.toFile())).data

        val matches = registry.findInterfaces("repo")
        assertEquals(listOf("com.example.Repository"), matches.map { it.value })
    }

    // [TEST-DONE] findInterfaces matches pattern case-insensitively

    @Test
    fun `skips synthetic and lambda classes`() {
        TestClassWriter.writeClassFile(
            tempDir.toFile(), "com/example/Service\$1", "Service.kt",
            interfaces = arrayOf("com/example/Callback"),
        )
        TestClassWriter.writeClassFile(
            tempDir.toFile(), "com/example/Service\$lambda\$0", "Service.kt",
            interfaces = arrayOf("com/example/Callback"),
        )
        TestClassWriter.writeClassFile(
            tempDir.toFile(), "com/example/RealImpl", "RealImpl.kt",
            interfaces = arrayOf("com/example/Callback"),
        )

        val registry = InterfaceRegistry.build(listOf(tempDir.toFile())).data

        val names = registry.implementorsOf(ClassName("com.example.Callback")).map { it.className.value }
        assertEquals(listOf("com.example.RealImpl"), names)
    }

    // [TEST-DONE] Skips synthetic and lambda classes

    @Test
    fun `returns implementors sorted by class name`() {
        TestClassWriter.writeClassFile(tempDir.toFile(), "com/example/Zebra", "Zebra.kt", interfaces = arrayOf("com/example/Animal"))
        TestClassWriter.writeClassFile(tempDir.toFile(), "com/example/Apple", "Apple.kt", interfaces = arrayOf("com/example/Animal"))
        TestClassWriter.writeClassFile(tempDir.toFile(), "com/example/Mango", "Mango.kt", interfaces = arrayOf("com/example/Animal"))

        val registry = InterfaceRegistry.build(listOf(tempDir.toFile())).data

        val names = registry.implementorsOf(ClassName("com.example.Animal")).map { it.className.value }
        assertEquals(listOf("com.example.Apple", "com.example.Mango", "com.example.Zebra"), names)
    }

    // [TEST-DONE] Returns implementors sorted by class name

    @Test
    fun `build merges implementors from multiple class directories`() {
        val mainDir = tempDir.resolve("main").toFile().also { it.mkdirs() }
        val testDir = tempDir.resolve("test").toFile().also { it.mkdirs() }

        TestClassWriter.writeClassFile(
            mainDir, "com/example/RealRepo", "RealRepo.kt",
            interfaces = arrayOf("com/example/Repository"),
        )
        TestClassWriter.writeClassFile(
            testDir, "com/example/FakeRepo", "FakeRepo.kt",
            interfaces = arrayOf("com/example/Repository"),
        )

        val registry = InterfaceRegistry.build(listOf(mainDir, testDir)).data

        val names = registry.implementorsOf(ClassName("com.example.Repository")).map { it.className.value }
        assertEquals(listOf("com.example.FakeRepo", "com.example.RealRepo"), names)
    }
}
