package no.f12.codenavigator.analysis

import java.io.File
import java.time.LocalDate

object GitLogRunner {

    fun run(projectDir: File, after: LocalDate, followRenames: Boolean = true): List<GitCommit> {
        val command = buildCommand(after, followRenames)
        val process = ProcessBuilder(command)
            .directory(projectDir)
            .redirectErrorStream(true)
            .start()

        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()

        if (exitCode != 0) {
            throw RuntimeException("git log failed (exit code $exitCode): $output")
        }

        return GitLogParser.parse(output)
    }

    internal fun buildCommand(after: LocalDate, followRenames: Boolean = true): List<String> =
        buildList {
            add("git")
            add("log")
            add("--all")
            add("--numstat")
            add("--date=short")
            add("--pretty=format:--%h--%ad--%aN")
            if (followRenames) {
                add("-M")
            } else {
                add("--no-renames")
            }
            add("--after=$after")
        }
}
