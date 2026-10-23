package no.f12.codenavigator.maven

import no.f12.codenavigator.config.OutputFormat
import no.f12.codenavigator.history.HistoryOptions
import no.f12.codenavigator.history.RunHistory
import no.f12.codenavigator.history.RunSnapshot
import no.f12.codenavigator.config.CnavConfig
import no.f12.codenavigator.navigation.dsm.PlanMutator
import no.f12.codenavigator.navigation.dsm.PlanStep
import no.f12.codenavigator.navigation.types.SourceSet
import no.f12.codenavigator.registry.ClassFileStaleness
import no.f12.codenavigator.registry.StalenessResult
import org.apache.maven.plugin.MojoFailureException
import org.apache.maven.project.MavenProject
import java.io.File

/**
 * Maven's own source roots, plus the Kotlin ones. A Kotlin Maven project normally keeps its code in
 * `src/main/kotlin`, but that directory only ends up in [MavenProject.getCompileSourceRoots] while
 * kotlin-maven-plugin is executing in the *same* Maven run — in a separate `mvn <goal>` invocation, which
 * is how every cnav goal is run, the list holds just the default `src/main/java`. Relying on it alone made
 * the source-based goals (move/rename/safe-delete, size, duplicates, find-declaration) scan nothing and
 * report "No changes needed". So the conventional Kotlin directories and any `<sourceDirs>` configured on
 * kotlin-maven-plugin are added.
 */
fun MavenProject.mainSourceRoots(): List<File> =
    distinctExisting(compileSourceRoots.map { File(it as String) } + File(basedir, "src/main/kotlin") + kotlinPluginSourceDirs(test = false))

fun MavenProject.testSourceRoots(): List<File> =
    distinctExisting(testCompileSourceRoots.map { File(it as String) } + File(basedir, "src/test/kotlin") + kotlinPluginSourceDirs(test = true))

fun MavenProject.allSourceRoots(): List<File> = distinctExisting(mainSourceRoots() + testSourceRoots())

private fun distinctExisting(dirs: List<File>): List<File> =
    dirs.filter { it.isDirectory }.distinctBy { it.canonicalPath }

private const val KOTLIN_MAVEN_PLUGIN = "org.jetbrains.kotlin:kotlin-maven-plugin"

/** `<sourceDirs><sourceDir>..` from kotlin-maven-plugin's configuration, for its compile or test-compile executions. */
private fun MavenProject.kotlinPluginSourceDirs(test: Boolean): List<File> = try {
    val plugin = build.plugins.firstOrNull { (it as org.apache.maven.model.Plugin).key == KOTLIN_MAVEN_PLUGIN } as? org.apache.maven.model.Plugin
    if (plugin == null) {
        emptyList()
    } else {
        val configurations = buildList {
            plugin.executions.forEach { execution ->
                val goals = (execution as org.apache.maven.model.PluginExecution).goals.map { it as String }
                val isTest = goals.any { it == "test-compile" }
                val isMain = goals.any { it == "compile" }
                if ((test && isTest) || (!test && isMain)) add(execution.configuration)
            }
            if (!test) add(plugin.configuration)
        }
        configurations.flatMap { configuration ->
            val dom = configuration as? org.codehaus.plexus.util.xml.Xpp3Dom
            dom?.getChild("sourceDirs")?.children?.mapNotNull { it.value?.trim()?.takeIf(String::isNotEmpty) }.orEmpty()
        }.map { path -> File(path).let { if (it.isAbsolute) it else File(basedir, path) } }
    }
} catch (_: Exception) {
    emptyList()
}

fun MavenProject.checkStaleness(log: org.apache.maven.plugin.logging.Log) {
    val sourceDirs = allSourceRoots()
    val classDirs = listOfNotNull(
        File(build.outputDirectory).takeIf { it.exists() },
        File(build.testOutputDirectory).takeIf { it.exists() },
    )
    when (val result = ClassFileStaleness.check(sourceDirs, classDirs)) {
        is StalenessResult.Fresh -> {}
        is StalenessResult.Stale -> log.warn("⚠ ${result.warning}")
        is StalenessResult.NoClassFiles -> throw MojoFailureException(result.error)
    }
}

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

/** Parses a `--plan-file` (if given) into plan steps. Returns an empty list when absent. */
fun loadPlanSteps(planFile: String?): List<PlanStep> =
    planFile?.let { PlanMutator.parseFile(File(it)) } ?: emptyList()

/** Merges cnav-config.json's `defaults` section under [properties] — explicit mojo properties always win. See [CnavConfig]. */
fun MavenProject.applyConfigDefaults(properties: Map<String, String?>): Map<String, String?> =
    CnavConfig.applyDefaults(properties, basedir)

/**
 * Prints how this analysis moved since an earlier run (stored under `<project>/.cnav/history`) and/or its trend,
 * unless `-Dno-compare` was given. Call right after the mojo's own output so it also shows on runs that go on
 * to fail the build.
 */
fun MavenProject.reportComparison(task: String, props: Map<String, String?>, snapshot: RunSnapshot, format: OutputFormat) {
    RunHistory.report(basedir, task, RunHistory.fingerprint(props), snapshot, format, HistoryOptions.from(props))
        ?.let { println(it) }
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
