package no.f12.codenavigator.navigation

import org.objectweb.asm.Opcodes
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SymbolExtractorTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `extracts a public method as a METHOD symbol`() {
        val classFile = TestClassWriter.writeClassFile(tempDir.toFile(), "com/example/MyService", "MyService.kt") {
            visitMethod(Opcodes.ACC_PUBLIC, "resetPassword", "()V", null, null)
        }

        val symbols = SymbolExtractor.extract(classFile)

        assertEquals(1, symbols.size)
        val symbol = symbols.first()
        assertEquals("com.example", symbol.packageName.value)
        assertEquals("MyService", symbol.className)
        assertEquals("resetPassword", symbol.symbolName)
        assertEquals(SymbolKind.METHOD, symbol.kind)
        assertEquals("MyService.kt", symbol.sourceFile)
    }

    // [TEST] Extracts a public method as a METHOD symbol -- DONE

    @Test
    fun `extracts a public field as a FIELD symbol`() {
        val classFile = TestClassWriter.writeClassFile(tempDir.toFile(), "com/example/UserInfo", "UserInfo.kt") {
            visitField(Opcodes.ACC_PUBLIC, "nationalId", "Ljava/lang/String;", null, null)
        }

        val symbols = SymbolExtractor.extract(classFile)

        assertEquals(1, symbols.size)
        val symbol = symbols.first()
        assertEquals("nationalId", symbol.symbolName)
        assertEquals(SymbolKind.FIELD, symbol.kind)
        assertEquals("UserInfo", symbol.className)
    }

    // [TEST] Extracts a public field as a FIELD symbol -- DONE
    // [TEST] Includes package, class name, symbol name, kind, and source file -- covered by first two tests
    // [TEST] Filters out constructors (init and clinit)

    @Test
    fun `filters out constructors`() {
        val classFile = TestClassWriter.writeClassFile(tempDir.toFile(), "com/example/Foo", "Foo.kt") {
            visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null)
            visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null)
            visitMethod(Opcodes.ACC_PUBLIC, "doWork", "()V", null, null)
        }

        val symbols = SymbolExtractor.extract(classFile)

        assertEquals(1, symbols.size)
        assertEquals("doWork", symbols.first().symbolName)
    }
    // [TEST] Filters out synthetic methods (access$ bridge methods)

    @Test
    fun `filters out synthetic and access bridge methods`() {
        val classFile = TestClassWriter.writeClassFile(tempDir.toFile(), "com/example/Foo", "Foo.kt") {
            visitMethod(Opcodes.ACC_STATIC or Opcodes.ACC_SYNTHETIC, "access\$getName", "()Ljava/lang/String;", null, null)
            visitMethod(Opcodes.ACC_PUBLIC or Opcodes.ACC_SYNTHETIC, "bridge\$method", "()V", null, null)
            visitMethod(Opcodes.ACC_PUBLIC, "realMethod", "()V", null, null)
        }

        val symbols = SymbolExtractor.extract(classFile)

        assertEquals(1, symbols.size)
        assertEquals("realMethod", symbols.first().symbolName)
    }

    @Test
    fun `filters out Kotlin property accessors for fields`() {
        val classFile = TestClassWriter.writeClassFile(tempDir.toFile(), "com/example/User", "User.kt") {
            visitField(Opcodes.ACC_PRIVATE, "name", "Ljava/lang/String;", null, null)
            visitMethod(Opcodes.ACC_PUBLIC, "getName", "()Ljava/lang/String;", null, null)
            visitMethod(Opcodes.ACC_PUBLIC, "setName", "(Ljava/lang/String;)V", null, null)
            visitMethod(Opcodes.ACC_PUBLIC, "doSomething", "()V", null, null)
        }

        val symbols = SymbolExtractor.extract(classFile)

        val symbolNames = symbols.map { it.symbolName }
        assertEquals(listOf("name", "doSomething"), symbolNames)
    }

    @Test
    fun `filters out data class generated methods`() {
        val classFile = TestClassWriter.writeClassFile(tempDir.toFile(), "com/example/Data", "Data.kt") {
            visitMethod(Opcodes.ACC_PUBLIC, "component1", "()Ljava/lang/String;", null, null)
            visitMethod(Opcodes.ACC_PUBLIC, "component2", "()I", null, null)
            visitMethod(Opcodes.ACC_PUBLIC, "copy", "(Ljava/lang/String;I)Lcom/example/Data;", null, null)
            visitMethod(Opcodes.ACC_PUBLIC, "toString", "()Ljava/lang/String;", null, null)
            visitMethod(Opcodes.ACC_PUBLIC, "hashCode", "()I", null, null)
            visitMethod(Opcodes.ACC_PUBLIC, "equals", "(Ljava/lang/Object;)Z", null, null)
            visitMethod(Opcodes.ACC_PUBLIC, "transform", "()V", null, null)
        }

        val symbols = SymbolExtractor.extract(classFile)

        assertEquals(1, symbols.size)
        assertEquals("transform", symbols.first().symbolName)
    }

    @Test
    fun `keeps non-accessor methods that start with get or set`() {
        val classFile = TestClassWriter.writeClassFile(tempDir.toFile(), "com/example/Service", "Service.kt") {
            visitMethod(Opcodes.ACC_PUBLIC, "getActiveUsers", "()Ljava/util/List;", null, null)
            visitMethod(Opcodes.ACC_PUBLIC, "setUpEnvironment", "()V", null, null)
        }

        val symbols = SymbolExtractor.extract(classFile)

        val symbolNames = symbols.map { it.symbolName }
        assertEquals(listOf("getActiveUsers", "setUpEnvironment"), symbolNames)
    }

    @Test
    fun `handles class with no methods or fields`() {
        val classFile = TestClassWriter.writeClassFile(tempDir.toFile(), "com/example/Empty", "Empty.kt") {}

        val symbols = SymbolExtractor.extract(classFile)

        assertTrue(symbols.isEmpty())
    }

    @Test
    fun `extracts symbols from class with both methods and fields`() {
        val classFile = TestClassWriter.writeClassFile(tempDir.toFile(), "com/example/Mixed", "Mixed.kt") {
            visitField(Opcodes.ACC_PUBLIC, "count", "I", null, null)
            visitMethod(Opcodes.ACC_PUBLIC, "increment", "()V", null, null)
        }

        val symbols = SymbolExtractor.extract(classFile)

        assertEquals(2, symbols.size)
        val field = symbols.first { it.kind == SymbolKind.FIELD }
        val method = symbols.first { it.kind == SymbolKind.METHOD }
        assertEquals("count", field.symbolName)
        assertEquals("increment", method.symbolName)
    }

    @Test
    fun `extracts static methods`() {
        val classFile = TestClassWriter.writeClassFile(tempDir.toFile(), "com/example/UtilsKt", "Utils.kt") {
            visitMethod(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "formatDate", "(Ljava/time/LocalDate;)Ljava/lang/String;", null, null)
        }

        val symbols = SymbolExtractor.extract(classFile)

        assertEquals(1, symbols.size)
        assertEquals("formatDate", symbols.first().symbolName)
        assertEquals("UtilsKt", symbols.first().className)
        assertEquals("Utils.kt", symbols.first().sourceFile)
    }

    @Test
    fun `filters out lambda-generated method names`() {
        val classFile = TestClassWriter.writeClassFile(tempDir.toFile(), "com/example/Service", "Service.kt") {
            visitMethod(Opcodes.ACC_PUBLIC, "doWork\$lambda\$0", "()V", null, null)
            visitMethod(Opcodes.ACC_PUBLIC, "doWork\$lambda\$0\$0", "()V", null, null)
            visitMethod(Opcodes.ACC_PUBLIC, "doWork", "()V", null, null)
        }

        val symbols = SymbolExtractor.extract(classFile)

        assertEquals(1, symbols.size)
        assertEquals("doWork", symbols.first().symbolName)
    }

    @Test
    fun `filters out companion object field INSTANCE`() {
        val classFile = TestClassWriter.writeClassFile(tempDir.toFile(), "com/example/Foo\$Companion", "Foo.kt") {
            visitField(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC or Opcodes.ACC_FINAL, "INSTANCE", "Lcom/example/Foo\$Companion;", null, null)
            visitMethod(Opcodes.ACC_PUBLIC, "create", "()Lcom/example/Foo;", null, null)
        }

        val symbols = SymbolExtractor.extract(classFile)

        assertEquals(1, symbols.size)
        assertEquals("create", symbols.first().symbolName)
    }
}
