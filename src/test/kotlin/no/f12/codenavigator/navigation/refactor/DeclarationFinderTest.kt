package no.f12.codenavigator.navigation.refactor

import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DeclarationFinderTest {

    @TempDir
    lateinit var tempDir: Path

    private fun sourceDir(): File = tempDir.resolve("src").toFile().also { it.mkdirs() }

    private fun write(srcDir: File, relativePath: String, content: String): File =
        File(srcDir, relativePath).also { it.parentFile.mkdirs(); it.writeText(content) }

    @Test
    fun `returns not found when no source files exist`() {
        val location = DeclarationFinder.locate(listOf(sourceDir()), "com.example.Foo")

        assertFalse(location.found)
        assertNull(location.declarationFile)
        assertTrue(location.callSiteFiles.isEmpty())
    }

    @Test
    fun `finds declaration file for a matching class`() {
        val src = sourceDir()
        val fooFile = write(src, "com/example/Foo.kt", """
            package com.example
            class Foo
        """.trimIndent())

        val location = DeclarationFinder.locate(listOf(src), "com.example.Foo")

        assertTrue(location.found)
        assertEquals(fooFile.canonicalPath, location.declarationFile?.canonicalPath)
        assertTrue(location.callSiteFiles.contains(fooFile))
    }

    @Test
    fun `includes same-package files in callSiteFiles even without explicit import`() {
        val src = sourceDir()
        val fooFile = write(src, "com/example/Foo.kt", "package com.example\nclass Foo")
        val barFile = write(src, "com/example/Bar.kt", "package com.example\nclass Bar")

        val location = DeclarationFinder.locate(listOf(src), "com.example.Foo")

        assertTrue(location.callSiteFiles.map { it.canonicalPath }.contains(barFile.canonicalPath),
            "Bar.kt is in the same package as Foo and must be in callSiteFiles")
    }

    @Test
    fun `includes files that import the target class`() {
        val src = sourceDir()
        write(src, "com/example/Foo.kt", "package com.example\nclass Foo")
        val callerFile = write(src, "other/Caller.kt", "package other\nimport com.example.Foo\nclass Caller(val f: Foo)")

        val location = DeclarationFinder.locate(listOf(src), "com.example.Foo")

        assertTrue(location.callSiteFiles.map { it.canonicalPath }.contains(callerFile.canonicalPath))
    }

    @Test
    fun `does not include unrelated files`() {
        val src = sourceDir()
        write(src, "com/example/Foo.kt", "package com.example\nclass Foo")
        val unrelated = write(src, "other/Unrelated.kt", "package other\nclass Unrelated")

        val location = DeclarationFinder.locate(listOf(src), "com.example.Foo")

        assertFalse(location.callSiteFiles.map { it.canonicalPath }.contains(unrelated.canonicalPath))
    }

    @Test
    fun `returns not found for unknown class`() {
        val src = sourceDir()
        write(src, "com/example/Foo.kt", "package com.example\nclass Foo")

        val location = DeclarationFinder.locate(listOf(src), "com.example.DoesNotExist")

        assertFalse(location.found)
        assertNull(location.declarationFile)
    }

    @Test
    fun `threads overrideFamilyFqns through to result`() {
        val src = sourceDir()
        write(src, "com/example/Foo.kt", "package com.example\nclass Foo")
        val family = setOf("com.example.FooImpl", "com.example.IFoo")

        val location = DeclarationFinder.locate(listOf(src), "com.example.Foo", overrideFamilyFqns = family)

        assertEquals(family, location.overrideFamilyFqns)
    }
}
