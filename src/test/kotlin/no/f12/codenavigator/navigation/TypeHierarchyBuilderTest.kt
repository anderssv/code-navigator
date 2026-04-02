package no.f12.codenavigator.navigation

import no.f12.codenavigator.navigation.core.ClassName
import no.f12.codenavigator.navigation.hierarchy.SupertypeKind
import no.f12.codenavigator.navigation.hierarchy.TypeHierarchyBuilder
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TypeHierarchyBuilderTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `builds hierarchy for class with no supertypes and no implementors`() {
        TestClassWriter.writeClassFile(tempDir.toFile(), "com/example/Simple", "Simple.kt")

        val results = TypeHierarchyBuilder.build(listOf(tempDir.toFile()), "Simple", projectOnly = false)

        assertEquals(1, results.size)
        val result = results.first()
        assertEquals(ClassName("com.example.Simple"), result.className)
        assertTrue(result.supertypes.isEmpty())
        assertTrue(result.implementors.isEmpty())
    }

    @Test
    fun `builds hierarchy showing direct superclass`() {
        TestClassWriter.writeClassFile(tempDir.toFile(), "com/example/Base", "Base.kt")
        TestClassWriter.writeClassFile(
            tempDir.toFile(), "com/example/Child", "Child.kt",
            superName = "com/example/Base",
        )

        val results = TypeHierarchyBuilder.build(listOf(tempDir.toFile()), "Child", projectOnly = false)

        assertEquals(1, results.size)
        val result = results.first()
        assertEquals(1, result.supertypes.size)
        val supertype = result.supertypes.first()
        assertEquals(ClassName("com.example.Base"), supertype.className)
        assertEquals(SupertypeKind.CLASS, supertype.kind)
    }

    @Test
    fun `builds hierarchy showing direct interfaces`() {
        TestClassWriter.writeClassFile(tempDir.toFile(), "com/example/Readable", "Readable.kt")
        TestClassWriter.writeClassFile(
            tempDir.toFile(), "com/example/MyClass", "MyClass.kt",
            interfaces = arrayOf("com/example/Readable"),
        )

        val results = TypeHierarchyBuilder.build(listOf(tempDir.toFile()), "MyClass", projectOnly = false)

        assertEquals(1, results.size)
        val iface = results.first().supertypes.first()
        assertEquals(ClassName("com.example.Readable"), iface.className)
        assertEquals(SupertypeKind.INTERFACE, iface.kind)
    }

    @Test
    fun `walks superclass chain recursively`() {
        TestClassWriter.writeClassFile(tempDir.toFile(), "com/example/Grandparent", "Grandparent.kt")
        TestClassWriter.writeClassFile(
            tempDir.toFile(), "com/example/Parent", "Parent.kt",
            superName = "com/example/Grandparent",
        )
        TestClassWriter.writeClassFile(
            tempDir.toFile(), "com/example/Child", "Child.kt",
            superName = "com/example/Parent",
        )

        val results = TypeHierarchyBuilder.build(listOf(tempDir.toFile()), "Child", projectOnly = false)

        val parent = results.first().supertypes.first()
        assertEquals(ClassName("com.example.Parent"), parent.className)
        assertEquals(1, parent.supertypes.size)
        val grandparent = parent.supertypes.first()
        assertEquals(ClassName("com.example.Grandparent"), grandparent.className)
    }

    @Test
    fun `walks interface chain recursively`() {
        TestClassWriter.writeClassFile(
            tempDir.toFile(), "com/example/BaseInterface", "BaseInterface.kt",
            access = org.objectweb.asm.Opcodes.ACC_PUBLIC or org.objectweb.asm.Opcodes.ACC_INTERFACE or org.objectweb.asm.Opcodes.ACC_ABSTRACT,
        )
        TestClassWriter.writeClassFile(
            tempDir.toFile(), "com/example/SubInterface", "SubInterface.kt",
            access = org.objectweb.asm.Opcodes.ACC_PUBLIC or org.objectweb.asm.Opcodes.ACC_INTERFACE or org.objectweb.asm.Opcodes.ACC_ABSTRACT,
            interfaces = arrayOf("com/example/BaseInterface"),
        )
        TestClassWriter.writeClassFile(
            tempDir.toFile(), "com/example/Impl", "Impl.kt",
            interfaces = arrayOf("com/example/SubInterface"),
        )

        val results = TypeHierarchyBuilder.build(listOf(tempDir.toFile()), "Impl", projectOnly = false)

        val subInterface = results.first().supertypes.first()
        assertEquals(ClassName("com.example.SubInterface"), subInterface.className)
        assertEquals(SupertypeKind.INTERFACE, subInterface.kind)
        val baseInterface = subInterface.supertypes.first()
        assertEquals(ClassName("com.example.BaseInterface"), baseInterface.className)
        assertEquals(SupertypeKind.INTERFACE, baseInterface.kind)
    }

    @Test
    fun `combines superclass and interfaces`() {
        TestClassWriter.writeClassFile(tempDir.toFile(), "com/example/Base", "Base.kt")
        TestClassWriter.writeClassFile(tempDir.toFile(), "com/example/Readable", "Readable.kt")
        TestClassWriter.writeClassFile(
            tempDir.toFile(), "com/example/MyClass", "MyClass.kt",
            superName = "com/example/Base",
            interfaces = arrayOf("com/example/Readable"),
        )

        val results = TypeHierarchyBuilder.build(listOf(tempDir.toFile()), "MyClass", projectOnly = false)

        val supertypes = results.first().supertypes
        assertEquals(2, supertypes.size)
        assertEquals(ClassName("com.example.Base"), supertypes[0].className)
        assertEquals(SupertypeKind.CLASS, supertypes[0].kind)
        assertEquals(ClassName("com.example.Readable"), supertypes[1].className)
        assertEquals(SupertypeKind.INTERFACE, supertypes[1].kind)
    }

    @Test
    fun `shows implementors for an interface`() {
        TestClassWriter.writeClassFile(
            tempDir.toFile(), "com/example/Repository", "Repository.kt",
            access = org.objectweb.asm.Opcodes.ACC_PUBLIC or org.objectweb.asm.Opcodes.ACC_INTERFACE or org.objectweb.asm.Opcodes.ACC_ABSTRACT,
        )
        TestClassWriter.writeClassFile(
            tempDir.toFile(), "com/example/UserRepo", "UserRepo.kt",
            interfaces = arrayOf("com/example/Repository"),
        )

        val results = TypeHierarchyBuilder.build(listOf(tempDir.toFile()), "Repository", projectOnly = false)

        assertEquals(1, results.size)
        val implementors = results.first().implementors
        assertEquals(1, implementors.size)
        assertEquals(ClassName("com.example.UserRepo"), implementors.first().className)
    }

    @Test
    fun `matches classes by pattern case-insensitively`() {
        TestClassWriter.writeClassFile(tempDir.toFile(), "com/example/MyService", "MyService.kt")
        TestClassWriter.writeClassFile(tempDir.toFile(), "com/example/OtherClass", "OtherClass.kt")

        val results = TypeHierarchyBuilder.build(listOf(tempDir.toFile()), "myservice", projectOnly = false)

        assertEquals(1, results.size)
        assertEquals(ClassName("com.example.MyService"), results.first().className)
    }

    @Test
    fun `filters non-project supertypes when projectOnly is true`() {
        TestClassWriter.writeClassFile(
            tempDir.toFile(), "com/example/MyClass", "MyClass.kt",
            interfaces = arrayOf("java/io/Serializable"),
        )

        val results = TypeHierarchyBuilder.build(listOf(tempDir.toFile()), "MyClass", projectOnly = true)

        assertTrue(results.first().supertypes.isEmpty())
    }

    @Test
    fun `includes non-project supertypes when projectOnly is false`() {
        TestClassWriter.writeClassFile(
            tempDir.toFile(), "com/example/MyClass", "MyClass.kt",
            interfaces = arrayOf("java/io/Serializable"),
        )

        val results = TypeHierarchyBuilder.build(listOf(tempDir.toFile()), "MyClass", projectOnly = false)

        assertEquals(1, results.first().supertypes.size)
        assertEquals(ClassName("java.io.Serializable"), results.first().supertypes.first().className)
    }

    @Test
    fun `returns empty list when pattern matches nothing`() {
        TestClassWriter.writeClassFile(tempDir.toFile(), "com/example/MyClass", "MyClass.kt")

        val results = TypeHierarchyBuilder.build(listOf(tempDir.toFile()), "NonExistent", projectOnly = false)

        assertTrue(results.isEmpty())
    }
}
