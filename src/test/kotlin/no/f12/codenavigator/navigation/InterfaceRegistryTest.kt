package no.f12.codenavigator.navigation.interfaces

import no.f12.codenavigator.navigation.core.ClassName
import no.f12.codenavigator.navigation.TestClassWriter

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

    // === externalInterfacesOf tests ===

    @Test
    fun `externalInterfacesOf returns external interfaces for project classes`() {
        TestClassWriter.writeClassFile(
            tempDir.toFile(), "com/example/Adapter", "Adapter.kt",
            interfaces = arrayOf("javax/xml/bind/XmlAdapter"),
        )

        val registry = InterfaceRegistry.build(listOf(tempDir.toFile())).data
        val projectClasses = setOf(ClassName("com.example.Adapter"))

        val external = registry.externalInterfacesOf(projectClasses)

        assertEquals(setOf(ClassName("javax.xml.bind.XmlAdapter")), external[ClassName("com.example.Adapter")])
    }

    @Test
    fun `externalInterfacesOf excludes in-scope interfaces`() {
        TestClassWriter.writeClassFile(
            tempDir.toFile(), "com/example/ServiceImpl", "ServiceImpl.kt",
            interfaces = arrayOf("com/example/Service"),
        )

        val registry = InterfaceRegistry.build(listOf(tempDir.toFile())).data
        val projectClasses = setOf(ClassName("com.example.ServiceImpl"), ClassName("com.example.Service"))

        val external = registry.externalInterfacesOf(projectClasses)

        assertTrue(external.isEmpty(), "In-scope interface should not appear in externalInterfacesOf")
    }

    @Test
    fun `externalInterfacesOf with mixed in-scope and external interfaces`() {
        TestClassWriter.writeClassFile(
            tempDir.toFile(), "com/example/Adapter", "Adapter.kt",
            interfaces = arrayOf("com/example/Service", "javax/xml/bind/XmlAdapter"),
        )

        val registry = InterfaceRegistry.build(listOf(tempDir.toFile())).data
        val projectClasses = setOf(ClassName("com.example.Adapter"), ClassName("com.example.Service"))

        val external = registry.externalInterfacesOf(projectClasses)

        assertEquals(setOf(ClassName("javax.xml.bind.XmlAdapter")), external[ClassName("com.example.Adapter")])
    }

    // === interfacesOf tests ===

    @Test
    fun `interfacesOf returns interfaces implemented by a class`() {
        TestClassWriter.writeClassFile(
            tempDir.toFile(), "com/example/ServiceImpl", "ServiceImpl.kt",
            interfaces = arrayOf("com/example/Service", "com/example/Auditable"),
        )

        val registry = InterfaceRegistry.build(listOf(tempDir.toFile())).data

        val interfaces = registry.interfacesOf(ClassName("com.example.ServiceImpl"))

        assertEquals(
            setOf(ClassName("com.example.Service"), ClassName("com.example.Auditable")),
            interfaces,
        )
    }

    @Test
    fun `interfacesOf returns empty set for class with no interfaces`() {
        TestClassWriter.writeClassFile(tempDir.toFile(), "com/example/Plain", "Plain.kt")

        val registry = InterfaceRegistry.build(listOf(tempDir.toFile())).data

        assertTrue(registry.interfacesOf(ClassName("com.example.Plain")).isEmpty())
    }

    @Test
    fun `interfacesOf returns empty set for unknown class`() {
        val registry = InterfaceRegistry.build(listOf(tempDir.toFile())).data

        assertTrue(registry.interfacesOf(ClassName("com.example.Unknown")).isEmpty())
    }

    // === implementorMap and classToInterfacesMap tests ===

    @Test
    fun `implementorMap returns class names only without source file info`() {
        TestClassWriter.writeClassFile(
            tempDir.toFile(), "com/example/UserRepo", "UserRepo.kt",
            interfaces = arrayOf("com/example/Repository"),
        )
        TestClassWriter.writeClassFile(
            tempDir.toFile(), "com/example/OrderRepo", "OrderRepo.kt",
            interfaces = arrayOf("com/example/Repository"),
        )

        val registry = InterfaceRegistry.build(listOf(tempDir.toFile())).data
        val map = registry.implementorMap()

        assertEquals(
            setOf(ClassName("com.example.OrderRepo"), ClassName("com.example.UserRepo")),
            map[ClassName("com.example.Repository")],
        )
    }

    @Test
    fun `classToInterfacesMap returns reverse mapping`() {
        TestClassWriter.writeClassFile(
            tempDir.toFile(), "com/example/ServiceImpl", "ServiceImpl.kt",
            interfaces = arrayOf("com/example/Service", "com/example/Auditable"),
        )

        val registry = InterfaceRegistry.build(listOf(tempDir.toFile())).data
        val map = registry.classToInterfacesMap()

        assertEquals(
            setOf(ClassName("com.example.Service"), ClassName("com.example.Auditable")),
            map[ClassName("com.example.ServiceImpl")],
        )
    }

    // === Superclass tracking tests ===

    // [TEST] Class extending an external superclass includes that superclass in externalInterfacesOf
    // [TEST] Class extending a project superclass does not include it in externalInterfacesOf
    // [TEST] Class extending external superclass AND implementing external interface returns both
    // [TEST] interfacesOf returns superclass along with interfaces for a class

    @Test
    fun `externalInterfacesOf includes external superclass`() {
        TestClassWriter.writeClassFile(
            tempDir.toFile(), "com/example/MyVerifier", "MyVerifier.kt",
            superName = "com/nimbusds/jwt/proc/DefaultJWTClaimsVerifier",
        )

        val registry = InterfaceRegistry.build(listOf(tempDir.toFile())).data
        val projectClasses = setOf(ClassName("com.example.MyVerifier"))

        val external = registry.externalInterfacesOf(projectClasses)

        assertEquals(
            setOf(ClassName("com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier")),
            external[ClassName("com.example.MyVerifier")],
        )
    }

    @Test
    fun `externalInterfacesOf excludes project superclass`() {
        TestClassWriter.writeClassFile(
            tempDir.toFile(), "com/example/BaseService", "BaseService.kt",
        )
        TestClassWriter.writeClassFile(
            tempDir.toFile(), "com/example/UserService", "UserService.kt",
            superName = "com/example/BaseService",
        )

        val registry = InterfaceRegistry.build(listOf(tempDir.toFile())).data
        val projectClasses = setOf(ClassName("com.example.BaseService"), ClassName("com.example.UserService"))

        val external = registry.externalInterfacesOf(projectClasses)

        assertTrue(external.isEmpty(), "In-scope superclass should not appear in externalInterfacesOf")
    }

    @Test
    fun `externalInterfacesOf includes both external superclass and external interface`() {
        TestClassWriter.writeClassFile(
            tempDir.toFile(), "com/example/MyVerifier", "MyVerifier.kt",
            superName = "com/nimbusds/jwt/proc/DefaultJWTClaimsVerifier",
            interfaces = arrayOf("com/nimbusds/jwt/proc/JWTClaimsSetVerifier"),
        )

        val registry = InterfaceRegistry.build(listOf(tempDir.toFile())).data
        val projectClasses = setOf(ClassName("com.example.MyVerifier"))

        val external = registry.externalInterfacesOf(projectClasses)

        assertEquals(
            setOf(
                ClassName("com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier"),
                ClassName("com.nimbusds.jwt.proc.JWTClaimsSetVerifier"),
            ),
            external[ClassName("com.example.MyVerifier")],
        )
    }

    @Test
    fun `interfacesOf returns superclass along with interfaces`() {
        TestClassWriter.writeClassFile(
            tempDir.toFile(), "com/example/MyVerifier", "MyVerifier.kt",
            superName = "com/nimbusds/jwt/proc/DefaultJWTClaimsVerifier",
            interfaces = arrayOf("com/nimbusds/jwt/proc/JWTClaimsSetVerifier"),
        )

        val registry = InterfaceRegistry.build(listOf(tempDir.toFile())).data

        val supertypes = registry.interfacesOf(ClassName("com.example.MyVerifier"))

        assertEquals(
            setOf(
                ClassName("com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier"),
                ClassName("com.nimbusds.jwt.proc.JWTClaimsSetVerifier"),
            ),
            supertypes,
        )
    }
}
