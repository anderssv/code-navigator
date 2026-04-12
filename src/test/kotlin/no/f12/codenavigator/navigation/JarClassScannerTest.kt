package no.f12.codenavigator.navigation

import no.f12.codenavigator.navigation.classinfo.ClassInfoExtractor
import no.f12.codenavigator.navigation.core.JarClassEntry
import no.f12.codenavigator.navigation.core.JarClassScanner
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JarClassScannerTest {

    @TempDir
    lateinit var tempDir: Path

    // [TEST] Scans a JAR file and returns class entries
    @Test
    fun `scans a JAR and returns class entries`() {
        val jarFile = createJar(
            "com/example/Foo.class" to classBytes("com/example/Foo", "Foo.kt"),
            "com/example/Bar.class" to classBytes("com/example/Bar", "Bar.kt"),
        )

        val entries = JarClassScanner.scan(jarFile)

        assertEquals(2, entries.size)
        val names = entries.map { it.entryName }.sorted()
        assertEquals(listOf("com/example/Bar.class", "com/example/Foo.class"), names)
    }

    // [TEST] Skips non-class entries
    @Test
    fun `skips non-class entries`() {
        val jarFile = createJar(
            "com/example/Foo.class" to classBytes("com/example/Foo", "Foo.kt"),
            "META-INF/MANIFEST.MF" to "Manifest-Version: 1.0".toByteArray(),
            "application.properties" to "key=value".toByteArray(),
        )

        val entries = JarClassScanner.scan(jarFile)

        assertEquals(1, entries.size)
        assertEquals("com/example/Foo.class", entries.first().entryName)
    }

    // [TEST] Returns empty list for JAR with no class entries
    @Test
    fun `returns empty list for JAR with no class entries`() {
        val jarFile = createJar(
            "META-INF/MANIFEST.MF" to "Manifest-Version: 1.0".toByteArray(),
        )

        val entries = JarClassScanner.scan(jarFile)

        assertTrue(entries.isEmpty())
    }

    // [TEST] Entry label includes JAR name and entry path
    @Test
    fun `entry label includes JAR name and entry path`() {
        val jarFile = createJar(
            "com/example/Foo.class" to classBytes("com/example/Foo", "Foo.kt"),
        )

        val entries = JarClassScanner.scan(jarFile)

        assertEquals("${jarFile.name}!/com/example/Foo.class", entries.first().label)
    }

    // [TEST] Bytes can be used with extractors
    @Test
    fun `bytes can be used with ClassInfoExtractor`() {
        val jarFile = createJar(
            "com/example/Foo.class" to classBytes("com/example/Foo", "Foo.kt"),
        )

        val entries = JarClassScanner.scan(jarFile)
        val info = ClassInfoExtractor.extract(entries.first().bytes)

        assertEquals("com.example.Foo", info.className.value)
        assertEquals("Foo.kt", info.sourceFileName)
    }

    private fun classBytes(className: String, sourceFile: String): ByteArray {
        val writer = ClassWriter(0)
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, className, null, "java/lang/Object", null)
        writer.visitSource(sourceFile, null)
        writer.visitEnd()
        return writer.toByteArray()
    }

    private fun createJar(vararg entries: Pair<String, ByteArray>): File {
        val jarFile = tempDir.resolve("test.jar").toFile()
        JarOutputStream(jarFile.outputStream()).use { jos ->
            for ((name, bytes) in entries) {
                jos.putNextEntry(JarEntry(name))
                jos.write(bytes)
                jos.closeEntry()
            }
        }
        return jarFile
    }
}
