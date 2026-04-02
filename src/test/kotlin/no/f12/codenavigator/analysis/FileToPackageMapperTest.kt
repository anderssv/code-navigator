package no.f12.codenavigator.analysis

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FileToPackageMapperTest {

    // [TEST] Java main source file maps to correct package
    // [TEST] Test source file maps to correct package
    // [TEST] File at source root (no subdirectory) maps to empty/root package
    // [TEST] File with deeply nested package maps correctly
    // [TEST] Non-source file returns null
    // [TEST] File with no recognized source root returns null

    @Test
    fun `Kotlin main source file maps to correct package`() {
        val result = FileToPackageMapper.map("src/main/kotlin/com/example/foo/Bar.kt")

        assertEquals("com.example.foo", result)
    }

    @Test
    fun `Java main source file maps to correct package`() {
        val result = FileToPackageMapper.map("src/main/java/com/example/service/MyService.java")

        assertEquals("com.example.service", result)
    }

    @Test
    fun `test source file maps to correct package`() {
        val result = FileToPackageMapper.map("src/test/kotlin/com/example/foo/BarTest.kt")

        assertEquals("com.example.foo", result)
    }

    @Test
    fun `file at source root maps to empty package`() {
        val result = FileToPackageMapper.map("src/main/kotlin/App.kt")

        assertEquals("", result)
    }

    @Test
    fun `deeply nested package maps correctly`() {
        val result = FileToPackageMapper.map("src/main/java/com/example/very/deep/nested/pkg/Thing.java")

        assertEquals("com.example.very.deep.nested.pkg", result)
    }

    @Test
    fun `non-source file returns null`() {
        val result = FileToPackageMapper.map("build.gradle.kts")

        assertNull(result)
    }

    @Test
    fun `file with no recognized source root returns null`() {
        val result = FileToPackageMapper.map("docs/README.md")

        assertNull(result)
    }
}
