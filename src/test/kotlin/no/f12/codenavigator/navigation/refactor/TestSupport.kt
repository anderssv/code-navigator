package no.f12.codenavigator.navigation.refactor

import kotlin.test.assertTrue
import java.io.File
import java.nio.file.Files

fun copyTestSources(label: String): File {
    val testProjectDir = File("test-project")
    val tempDir = Files.createTempDirectory("cnav-test-$label").toFile()

    val srcDir = File(testProjectDir, "src")
    srcDir.copyRecursively(File(tempDir, "src"))

    File(testProjectDir, "build.gradle.kts").copyTo(File(tempDir, "build.gradle.kts"))
    File(testProjectDir, "settings.gradle.kts").copyTo(File(tempDir, "settings.gradle.kts"))
    File(testProjectDir, "gradle").copyRecursively(File(tempDir, "gradle"))
    File(testProjectDir, "gradlew").copyTo(File(tempDir, "gradlew"))
    File(tempDir, "gradlew").setExecutable(true)

    return tempDir
}

fun compileKotlin(projectDir: File) {
    val process = ProcessBuilder("mise", "exec", "--", "./gradlew", "compileKotlin", "--no-daemon")
        .directory(projectDir)
        .redirectErrorStream(true)
        .start()

    val output = process.inputStream.bufferedReader().readText()
    val exitCode = process.waitFor()

    assertTrue(exitCode == 0, "Compilation failed (exit $exitCode):\n$output")
}
