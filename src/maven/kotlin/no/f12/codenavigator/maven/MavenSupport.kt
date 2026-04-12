package no.f12.codenavigator.maven

import no.f12.codenavigator.navigation.core.SourceSet
import org.apache.maven.plugin.MojoFailureException
import org.apache.maven.project.MavenProject
import java.io.File

fun MavenProject.taggedClassDirectories(): List<Pair<File, SourceSet>> {
    val result = mutableListOf<Pair<File, SourceSet>>()
    val mainDir = File(build.outputDirectory)
    if (mainDir.exists()) {
        result.add(mainDir to SourceSet.MAIN)
    }
    val testDir = File(build.testOutputDirectory)
    if (testDir.exists()) {
        result.add(testDir to SourceSet.TEST)
    }
    return result
}

fun MavenProject.resolveJar(jarValue: String): File {
    if (isArtifactCoordinate(jarValue)) {
        return resolveArtifactFromClasspath(jarValue)
    }
    val jarFile = File(jarValue)
    if (!jarFile.exists()) {
        throw MojoFailureException("JAR file not found: $jarValue (resolved to ${jarFile.absolutePath})")
    }
    return jarFile
}

internal fun isArtifactCoordinate(value: String): Boolean {
    if (!value.contains(':')) return false
    if (value.startsWith("/")) return false
    if (value.length >= 2 && value[1] == ':' && value[0].isLetter()) return false
    return true
}

private fun MavenProject.resolveArtifactFromClasspath(coordinate: String): File {
    val parts = coordinate.split(":")
    if (parts.size < 2) {
        throw MojoFailureException("Invalid artifact coordinate: '$coordinate'. Expected format: group:name")
    }
    val group = parts[0]
    val name = parts[1]

    val groupPath = group.replace('.', '/')

    @Suppress("UNCHECKED_CAST")
    val classpathElements = runtimeClasspathElements as? List<String> ?: emptyList()

    val match = classpathElements
        .map { File(it) }
        .filter { it.isFile && it.extension == "jar" }
        .firstOrNull { jar ->
            val path = jar.absolutePath
            path.contains("/$groupPath/") && path.contains("/$name/")
        }

    if (match == null) {
        throw MojoFailureException(
            "Artifact '$coordinate' not found in runtime classpath. " +
                "Make sure it is declared as a dependency in your build.",
        )
    }
    return match
}
