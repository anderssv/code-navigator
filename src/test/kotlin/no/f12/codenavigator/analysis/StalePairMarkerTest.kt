package no.f12.codenavigator.analysis

import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StalePairMarkerTest {

    @TempDir
    lateinit var projectDir: File

    private fun touch(relativePath: String) {
        val file = File(projectDir, relativePath)
        file.parentFile.mkdirs()
        file.writeText("x")
    }

    @Test
    fun `pair with both files present is not stale`() {
        touch("src/main/kotlin/Foo.kt")
        touch("src/main/kotlin/Bar.kt")
        val pairs = listOf(CoupledPair("src/main/kotlin/Foo.kt", "src/main/kotlin/Bar.kt", 80, 8, 10))

        val result = StalePairMarker.mark(pairs, projectDir)

        assertFalse(result.single().stale)
    }

    @Test
    fun `pair with a missing file is marked stale`() {
        touch("src/main/kotlin/Foo.kt")
        // Bar.kt was renamed/deleted — no longer on disk
        val pairs = listOf(CoupledPair("src/main/kotlin/Foo.kt", "services/interfaces/Bar.kt", 80, 8, 10))

        val result = StalePairMarker.mark(pairs, projectDir)

        assertTrue(result.single().stale)
    }

    @Test
    fun `pair with both files missing is marked stale`() {
        val pairs = listOf(CoupledPair("gone/A.kt", "gone/B.kt", 80, 8, 10))

        val result = StalePairMarker.mark(pairs, projectDir)

        assertTrue(result.single().stale)
    }
}
