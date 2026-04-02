package no.f12.codenavigator.analysis

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PackageVolatilityBuilderTest {

    @Test
    fun `empty hotspot list produces empty result`() {
        val result = PackageVolatilityBuilder.build(emptyList())

        assertTrue(result.entries.isEmpty())
    }

    @Test
    fun `single file produces one package entry`() {
        val hotspots = listOf(
            Hotspot("src/main/kotlin/com/example/foo/Bar.kt", revisions = 5, totalChurn = 100),
        )

        val result = PackageVolatilityBuilder.build(hotspots)

        assertEquals(1, result.entries.size)
        val entry = result.entries[0]
        assertEquals("com.example.foo", entry.packageName)
        assertEquals(5, entry.revisions)
        assertEquals(100, entry.totalChurn)
        assertEquals(1, entry.fileCount)
        assertEquals(5.0, entry.avgRevisionsPerFile)
    }

    @Test
    fun `multiple files in same package aggregates correctly`() {
        val hotspots = listOf(
            Hotspot("src/main/kotlin/com/example/foo/Bar.kt", revisions = 5, totalChurn = 100),
            Hotspot("src/main/kotlin/com/example/foo/Baz.kt", revisions = 3, totalChurn = 50),
        )

        val result = PackageVolatilityBuilder.build(hotspots)

        assertEquals(1, result.entries.size)
        val entry = result.entries[0]
        assertEquals("com.example.foo", entry.packageName)
        assertEquals(8, entry.revisions)
        assertEquals(150, entry.totalChurn)
        assertEquals(2, entry.fileCount)
        assertEquals(4.0, entry.avgRevisionsPerFile)
    }

    @Test
    fun `files in different packages produce separate entries`() {
        val hotspots = listOf(
            Hotspot("src/main/kotlin/com/example/foo/Foo.kt", revisions = 5, totalChurn = 100),
            Hotspot("src/main/kotlin/com/example/bar/Bar.kt", revisions = 3, totalChurn = 50),
        )

        val result = PackageVolatilityBuilder.build(hotspots)

        assertEquals(2, result.entries.size)
        assertEquals("com.example.foo", result.entries[0].packageName)
        assertEquals("com.example.bar", result.entries[1].packageName)
    }

    @Test
    fun `results are sorted by total revisions descending`() {
        val hotspots = listOf(
            Hotspot("src/main/kotlin/com/example/low/A.kt", revisions = 2, totalChurn = 10),
            Hotspot("src/main/kotlin/com/example/high/B.kt", revisions = 20, totalChurn = 500),
            Hotspot("src/main/kotlin/com/example/mid/C.kt", revisions = 8, totalChurn = 100),
        )

        val result = PackageVolatilityBuilder.build(hotspots)

        assertEquals("com.example.high", result.entries[0].packageName)
        assertEquals("com.example.mid", result.entries[1].packageName)
        assertEquals("com.example.low", result.entries[2].packageName)
    }

    @Test
    fun `top parameter limits results`() {
        val hotspots = listOf(
            Hotspot("src/main/kotlin/com/example/a/A.kt", revisions = 10, totalChurn = 100),
            Hotspot("src/main/kotlin/com/example/b/B.kt", revisions = 5, totalChurn = 50),
            Hotspot("src/main/kotlin/com/example/c/C.kt", revisions = 1, totalChurn = 10),
        )

        val result = PackageVolatilityBuilder.build(hotspots, top = 2)

        assertEquals(2, result.entries.size)
        assertEquals("com.example.a", result.entries[0].packageName)
        assertEquals("com.example.b", result.entries[1].packageName)
    }

    @Test
    fun `files with no recognized source root are excluded`() {
        val hotspots = listOf(
            Hotspot("src/main/kotlin/com/example/foo/Bar.kt", revisions = 5, totalChurn = 100),
            Hotspot("build.gradle.kts", revisions = 3, totalChurn = 20),
            Hotspot("docs/README.md", revisions = 10, totalChurn = 200),
        )

        val result = PackageVolatilityBuilder.build(hotspots)

        assertEquals(1, result.entries.size)
        assertEquals("com.example.foo", result.entries[0].packageName)
    }

    @Test
    fun `average revisions per file is computed correctly`() {
        val hotspots = listOf(
            Hotspot("src/main/kotlin/com/example/foo/A.kt", revisions = 10, totalChurn = 200),
            Hotspot("src/main/kotlin/com/example/foo/B.kt", revisions = 6, totalChurn = 100),
            Hotspot("src/main/kotlin/com/example/foo/C.kt", revisions = 2, totalChurn = 30),
        )

        val result = PackageVolatilityBuilder.build(hotspots)

        assertEquals(1, result.entries.size)
        val entry = result.entries[0]
        assertEquals(3, entry.fileCount)
        assertEquals(18, entry.revisions)
        assertEquals(6.0, entry.avgRevisionsPerFile)
    }
}
