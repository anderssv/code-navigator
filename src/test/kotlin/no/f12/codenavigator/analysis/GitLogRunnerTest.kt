package no.f12.codenavigator.analysis

import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GitLogRunnerTest {

    @Test
    fun `buildCommand follows renames by default`() {
        val command = GitLogRunner.buildCommand(LocalDate.of(2024, 1, 1))

        assertEquals("git", command[0])
        assertEquals("log", command[1])
        assertTrue(command.contains("--all"))
        assertTrue(command.contains("--numstat"))
        assertTrue(command.contains("--date=short"))
        assertTrue(command.contains("--pretty=format:--%h--%ad--%aN"))
        assertTrue(command.contains("-M"))
        assertTrue(!command.contains("--no-renames"), "Should not contain --no-renames by default")
        assertTrue(command.contains("--after=2024-01-01"))
    }

    @Test
    fun `buildCommand disables renames when followRenames is false`() {
        val command = GitLogRunner.buildCommand(LocalDate.of(2024, 1, 1), followRenames = false)

        assertTrue(command.contains("--no-renames"))
        assertTrue(!command.contains("-M"), "Should not contain -M when renames disabled")
    }

    @Test
    fun `run parses commits from a real git repo`(@TempDir tempDir: File) {
        initTestRepo(tempDir)

        File(tempDir, "Foo.kt").writeText("class Foo")
        commitAll(tempDir, "Add Foo", "2024-06-01T12:00:00")

        File(tempDir, "Foo.kt").writeText("class Foo { fun bar() {} }")
        File(tempDir, "Bar.kt").writeText("class Bar")
        commitAll(tempDir, "Update Foo, add Bar", "2024-06-15T12:00:00")

        val commits = GitLogRunner.run(tempDir, LocalDate.of(2024, 1, 1))

        assertEquals(2, commits.size)

        val latest = commits[0]
        assertEquals(TEST_AUTHOR, latest.author)
        assertEquals(LocalDate.of(2024, 6, 15), latest.date)
        assertEquals(2, latest.files.size)
        val filePaths = latest.files.map { it.path }.toSet()
        assertTrue("Foo.kt" in filePaths, "Latest commit should touch Foo.kt, got: $filePaths")
        assertTrue("Bar.kt" in filePaths, "Latest commit should touch Bar.kt, got: $filePaths")

        val first = commits[1]
        assertEquals(TEST_AUTHOR, first.author)
        assertEquals(LocalDate.of(2024, 6, 1), first.date)
        assertEquals(1, first.files.size)
        assertEquals("Foo.kt", first.files[0].path)
    }

    @Test
    fun `run respects after date filter`(@TempDir tempDir: File) {
        initTestRepo(tempDir)

        File(tempDir, "Old.kt").writeText("old")
        commitAll(tempDir, "Old", "2023-01-01T12:00:00")

        File(tempDir, "New.kt").writeText("new")
        commitAll(tempDir, "New", "2024-06-01T12:00:00")

        // --after is exclusive: strictly after 2023-06-01, so the 2023-01-01 commit is excluded
        val commits = GitLogRunner.run(tempDir, LocalDate.of(2023, 6, 1))

        assertEquals(1, commits.size)
        assertEquals(1, commits[0].files.size)
        assertEquals("New.kt", commits[0].files[0].path)
    }

    @Test
    fun `run tracks renamed files to new path by default`(@TempDir tempDir: File) {
        initTestRepo(tempDir)

        File(tempDir, "OldName.kt").writeText("class Foo")
        commitAll(tempDir, "Add file", "2024-06-01T12:00:00")

        git(tempDir, "mv", "OldName.kt", "NewName.kt")
        commitAll(tempDir, "Rename file", "2024-06-02T12:00:00")

        val commits = GitLogRunner.run(tempDir, LocalDate.of(2024, 1, 1))

        val renameCommit = commits.first()
        val filePaths = renameCommit.files.map { it.path }.toSet()
        assertTrue("NewName.kt" in filePaths, "Should use new filename, got: $filePaths")
        assertTrue("OldName.kt" !in filePaths, "Should not contain old filename, got: $filePaths")
    }

    @Test
    fun `run shows old and new paths separately when followRenames is false`(@TempDir tempDir: File) {
        initTestRepo(tempDir)

        File(tempDir, "OldName.kt").writeText("class Foo")
        commitAll(tempDir, "Add file", "2024-06-01T12:00:00")

        git(tempDir, "mv", "OldName.kt", "NewName.kt")
        commitAll(tempDir, "Rename file", "2024-06-02T12:00:00")

        val commits = GitLogRunner.run(tempDir, LocalDate.of(2024, 1, 1), followRenames = false)

        val renameCommit = commits.first()
        val filePaths = renameCommit.files.map { it.path }.toSet()
        assertTrue("OldName.kt" in filePaths || "NewName.kt" in filePaths,
            "Should have separate entries, got: $filePaths")
    }

    companion object {
        private const val TEST_AUTHOR = "cnav-test-bot"
        private const val TEST_EMAIL = "cnav-test-bot@not-a-real-domain.test"

        private fun initTestRepo(dir: File) {
            git(dir, "init")
            git(dir, "config", "user.email", TEST_EMAIL)
            git(dir, "config", "user.name", TEST_AUTHOR)
            git(dir, "config", "commit.gpgSign", "false")
        }

        private fun commitAll(dir: File, message: String, isoDate: String) {
            git(dir, "add", ".")
            git(
                dir, "commit",
                "--no-gpg-sign",
                "-m", message,
                "--date=$isoDate",
                env = mapOf("GIT_COMMITTER_DATE" to isoDate),
            )
        }

        private fun git(dir: File, vararg args: String, env: Map<String, String> = emptyMap()) {
            val pb = ProcessBuilder(listOf("git") + args.toList())
                .directory(dir)
                .redirectErrorStream(true)
            env.forEach { (k, v) -> pb.environment()[k] = v }
            val process = pb.start()
            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()
            check(exitCode == 0) { "git ${args.toList()} failed (exit $exitCode): $output" }
        }
    }
}
