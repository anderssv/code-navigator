package no.f12.codenavigator.analysis

import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HotspotBuilderTest {

    @Test
    fun `empty commits returns empty list`() {
        val result = HotspotBuilder.build(emptyList())

        assertEquals(emptyList(), result)
    }

    @Test
    fun `single commit with one file returns one hotspot`() {
        val commits = listOf(
            GitCommit("abc", LocalDate.of(2024, 1, 1), "Author", listOf(FileChange(10, 5, "src/Foo.kt")))
        )

        val result = HotspotBuilder.build(commits)

        assertEquals(1, result.size)
        assertEquals(Hotspot("src/Foo.kt", revisions = 1, totalChurn = 15), result[0])
    }

    @Test
    fun `multiple commits touching same file aggregates revisions and churn`() {
        val commits = listOf(
            GitCommit("a", LocalDate.of(2024, 1, 1), "Author", listOf(FileChange(10, 5, "src/Foo.kt"))),
            GitCommit("b", LocalDate.of(2024, 1, 2), "Author", listOf(FileChange(3, 2, "src/Foo.kt"))),
            GitCommit("c", LocalDate.of(2024, 1, 3), "Author", listOf(FileChange(1, 0, "src/Foo.kt"))),
        )

        val result = HotspotBuilder.build(commits)

        assertEquals(1, result.size)
        assertEquals(Hotspot("src/Foo.kt", revisions = 3, totalChurn = 21), result[0])
    }

    @Test
    fun `results are sorted by revision count descending`() {
        val commits = listOf(
            GitCommit("a", LocalDate.of(2024, 1, 1), "Author", listOf(
                FileChange(1, 0, "src/Rarely.kt"),
                FileChange(1, 0, "src/Often.kt"),
            )),
            GitCommit("b", LocalDate.of(2024, 1, 2), "Author", listOf(FileChange(1, 0, "src/Often.kt"))),
            GitCommit("c", LocalDate.of(2024, 1, 3), "Author", listOf(FileChange(1, 0, "src/Often.kt"))),
        )

        val result = HotspotBuilder.build(commits)

        assertEquals(2, result.size)
        assertEquals("src/Often.kt", result[0].file)
        assertEquals(3, result[0].revisions)
        assertEquals("src/Rarely.kt", result[1].file)
        assertEquals(1, result[1].revisions)
    }

    @Test
    fun `top parameter limits results`() {
        val commits = listOf(
            GitCommit("a", LocalDate.of(2024, 1, 1), "Author", listOf(
                FileChange(1, 0, "src/A.kt"),
                FileChange(1, 0, "src/B.kt"),
                FileChange(1, 0, "src/C.kt"),
            )),
        )

        val result = HotspotBuilder.build(commits, top = 2)

        assertEquals(2, result.size)
    }

    @Test
    fun `min-revs parameter filters out low-revision files`() {
        val commits = listOf(
            GitCommit("a", LocalDate.of(2024, 1, 1), "Author", listOf(
                FileChange(1, 0, "src/Rare.kt"),
                FileChange(1, 0, "src/Common.kt"),
            )),
            GitCommit("b", LocalDate.of(2024, 1, 2), "Author", listOf(
                FileChange(1, 0, "src/Common.kt"),
            )),
        )

        val result = HotspotBuilder.build(commits, minRevs = 2)

        assertEquals(1, result.size)
        assertEquals("src/Common.kt", result[0].file)
    }

    // === rename merging ===

    @Test
    fun `revisions before and after a rename are merged under the new path`() {
        val commits = listOf(
            GitCommit("a", LocalDate.of(2024, 1, 1), "Author", listOf(FileChange(10, 5, "src/old/Foo.kt"))),
            GitCommit("b", LocalDate.of(2024, 1, 2), "Author", listOf(FileChange(3, 2, "src/old/Foo.kt"))),
            GitCommit("c", LocalDate.of(2024, 1, 3), "Author", listOf(FileChange(1, 0, "src/new/Foo.kt", renamedFrom = "src/old/Foo.kt"))),
            GitCommit("d", LocalDate.of(2024, 1, 4), "Author", listOf(FileChange(2, 0, "src/new/Foo.kt"))),
        )

        val result = HotspotBuilder.build(commits)

        assertEquals(1, result.size, "Old and new path must merge into a single entry, got: $result")
        assertEquals(Hotspot("src/new/Foo.kt", revisions = 4, totalChurn = 23), result[0])
    }

    @Test
    fun `revisions merge across a transitive rename chain`() {
        val commits = listOf(
            GitCommit("a", LocalDate.of(2024, 1, 1), "Author", listOf(FileChange(10, 0, "src/A.kt"))),
            GitCommit("b", LocalDate.of(2024, 1, 2), "Author", listOf(FileChange(1, 0, "src/B.kt", renamedFrom = "src/A.kt"))),
            GitCommit("c", LocalDate.of(2024, 1, 3), "Author", listOf(FileChange(1, 0, "src/C.kt", renamedFrom = "src/B.kt"))),
        )

        val result = HotspotBuilder.build(commits)

        assertEquals(1, result.size, "A->B->C chain must collapse into one entry, got: $result")
        assertEquals("src/C.kt", result[0].file)
        assertEquals(3, result[0].revisions)
    }

    @Test
    fun `unrelated files with the same name as a rename target are not merged`() {
        val commits = listOf(
            GitCommit("a", LocalDate.of(2024, 1, 1), "Author", listOf(FileChange(1, 0, "src/Other.kt"))),
            GitCommit("b", LocalDate.of(2024, 1, 2), "Author", listOf(FileChange(1, 0, "src/New.kt", renamedFrom = "src/Old.kt"))),
        )

        val result = HotspotBuilder.build(commits)

        assertEquals(2, result.size)
        assertTrue(result.any { it.file == "src/Other.kt" })
        assertTrue(result.any { it.file == "src/New.kt" })
    }

    // === projectDir existence filtering ===

    @Test
    fun `files that no longer exist are excluded when projectDir is provided`(@TempDir tempDir: Path) {
        val projectDir = tempDir.toFile()
        File(projectDir, "src").mkdirs()
        File(projectDir, "src/Existing.kt").writeText("class Existing")

        val commits = listOf(
            GitCommit("a", LocalDate.of(2024, 1, 1), "Author", listOf(
                FileChange(1, 0, "src/Existing.kt"),
                FileChange(1, 0, "src/Deleted.kt"),
            )),
        )

        val result = HotspotBuilder.build(commits, projectDir = projectDir)

        assertEquals(listOf("src/Existing.kt"), result.map { it.file })
    }

    @Test
    fun `renamed file is only reported at its current existing path`(@TempDir tempDir: Path) {
        val projectDir = tempDir.toFile()
        File(projectDir, "src/new").mkdirs()
        File(projectDir, "src/new/Foo.kt").writeText("class Foo")

        val commits = listOf(
            GitCommit("a", LocalDate.of(2024, 1, 1), "Author", listOf(FileChange(10, 5, "src/old/Foo.kt"))),
            GitCommit("b", LocalDate.of(2024, 1, 2), "Author", listOf(FileChange(1, 0, "src/new/Foo.kt", renamedFrom = "src/old/Foo.kt"))),
        )

        val result = HotspotBuilder.build(commits, projectDir = projectDir)

        assertEquals(1, result.size)
        assertEquals("src/new/Foo.kt", result[0].file)
        assertEquals(2, result[0].revisions, "Both the pre- and post-rename revisions should count")
    }

    @Test
    fun `without projectDir, deleted files are still included`() {
        val commits = listOf(
            GitCommit("a", LocalDate.of(2024, 1, 1), "Author", listOf(FileChange(1, 0, "src/Gone.kt"))),
        )

        val result = HotspotBuilder.build(commits)

        assertEquals(listOf("src/Gone.kt"), result.map { it.file })
    }
}
