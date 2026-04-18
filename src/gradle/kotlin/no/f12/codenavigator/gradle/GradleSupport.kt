package no.f12.codenavigator.gradle

import no.f12.codenavigator.registry.TaskDef
import no.f12.codenavigator.registry.TaskRegistry
import no.f12.codenavigator.navigation.core.SourceSet
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.tasks.SourceSetContainer
import java.io.File

fun Project.codeNavigatorExtension(): CodeNavigatorExtension =
    extensions.getByType(CodeNavigatorExtension::class.java)

fun Project.buildPropertyMap(
    taskDef: TaskDef,
): Map<String, String?> {
    val propertyNames = taskDef.params.filter { !it.flag }.map { it.name }
    val flagNames = taskDef.params.filter { it.flag }.map { it.name }
    val raw = buildPropertyMap(propertyNames, flagNames)

    val allCnavParamNames = TaskRegistry.ALL_TASKS.flatMap { it.params }.map { it.name }.toSet()
    val cliProperties = project.gradle.startParameter.projectProperties.keys
    val presentCnavProperties = allCnavParamNames.intersect(cliProperties)
    val warnings = taskDef.warnUnsupportedProperties(presentCnavProperties)
    require(warnings.isEmpty()) { warnings.joinToString("\n") }

    return taskDef.enhanceProperties(raw)
}

fun Project.taggedClassDirectories(): List<Pair<File, SourceSet>> {
    val sourceSets = extensions.getByType(SourceSetContainer::class.java)
    val result = mutableListOf<Pair<File, SourceSet>>()

    val mainSourceSet = sourceSets.getByName("main")
    mainSourceSet.output.classesDirs.files.forEach { dir ->
        result.add(dir to SourceSet.MAIN)
    }

    val testSourceSet = sourceSets.findByName("test")
    testSourceSet?.output?.classesDirs?.files
        ?.filter { it.exists() }
        ?.forEach { dir -> result.add(dir to SourceSet.TEST) }

    return result
}

fun Project.sourceDirectories(): List<File> {
    val sourceSets = extensions.getByType(SourceSetContainer::class.java)
    val dirs = mutableListOf<File>()

    for (sourceSet in sourceSets) {
        for (dir in sourceSet.allSource.srcDirs) {
            if (dir.exists()) {
                dirs.add(dir)
            }
        }
    }

    return dirs
}

fun Project.taggedSourceDirectories(): List<Pair<File, SourceSet>> {
    val sourceSets = extensions.getByType(SourceSetContainer::class.java)
    val result = mutableListOf<Pair<File, SourceSet>>()

    val mainSourceSet = sourceSets.getByName("main")
    for (dir in mainSourceSet.allSource.srcDirs) {
        if (dir.exists()) result.add(dir to SourceSet.MAIN)
    }

    val testSourceSet = sourceSets.findByName("test")
    testSourceSet?.allSource?.srcDirs
        ?.filter { it.exists() }
        ?.forEach { dir -> result.add(dir to SourceSet.TEST) }

    return result
}

private fun Project.buildPropertyMap(
    propertyNames: List<String>,
    flagNames: List<String>,
): Map<String, String?> {
    val map = mutableMapOf<String, String?>()
    for (name in propertyNames) {
        findProperty(name)?.let { map[name] = it.toString() }
    }
    for (name in flagNames) {
        if (hasProperty(name)) {
            map[name] = null
        }
    }
    return map
}

/**
 * Resolves a `-Pjar` value to a [File].
 *
 * - If the value contains `:` and doesn't start with `/` or a drive letter → treat as artifact coordinate
 *   (group:name), resolve the first matching JAR from the `runtimeClasspath` configuration.
 * - Otherwise → treat as a file path.
 */
fun Project.resolveJar(jarValue: String): File {
    if (isArtifactCoordinate(jarValue)) {
        return resolveArtifactFromClasspath(jarValue)
    }
    val jarFile = file(jarValue)
    if (!jarFile.exists()) {
        throw GradleException("JAR file not found: $jarValue (resolved to ${jarFile.absolutePath})")
    }
    return jarFile
}

private fun isArtifactCoordinate(value: String): Boolean {
    if (!value.contains(':')) return false
    // Absolute paths on Unix start with /, on Windows with drive letter like C:
    if (value.startsWith("/")) return false
    if (value.length >= 2 && value[1] == ':' && value[0].isLetter()) return false
    return true
}

private fun Project.resolveArtifactFromClasspath(coordinate: String): File {
    val parts = coordinate.split(":")
    if (parts.size < 2) {
        throw GradleException("Invalid artifact coordinate: '$coordinate'. Expected format: group:name")
    }
    val group = parts[0]
    val name = parts[1]

    val runtimeClasspath = configurations.findByName("runtimeClasspath")
        ?: throw GradleException("Cannot resolve artifact '$coordinate': no 'runtimeClasspath' configuration found.")

    val resolvedFiles = runtimeClasspath.resolvedConfiguration.resolvedArtifacts
        .filter { it.moduleVersion.id.group == group && it.moduleVersion.id.name == name }
        .map { it.file }

    if (resolvedFiles.isEmpty()) {
        throw GradleException(
            "Artifact '$coordinate' not found in runtimeClasspath. " +
                "Make sure it is declared as a dependency in your build."
        )
    }
    return resolvedFiles.first()
}
