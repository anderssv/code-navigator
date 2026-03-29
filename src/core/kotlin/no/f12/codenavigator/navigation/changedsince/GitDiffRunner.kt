package no.f12.codenavigator.navigation.changedsince

import java.io.File

object GitDiffRunner {

    fun run(projectDir: File, ref: String): List<String> {
        val command = buildCommand(ref)
        val process = ProcessBuilder(command)
            .directory(projectDir)
            .redirectErrorStream(true)
            .start()

        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()

        if (exitCode != 0) {
            throw RuntimeException("git diff failed (exit code $exitCode): $output")
        }

        return parseOutput(output)
    }

    internal fun buildCommand(ref: String): List<String> =
        listOf("git", "diff", "--name-only", "$ref...HEAD")

    internal fun parseOutput(output: String): List<String> =
        output.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
}
