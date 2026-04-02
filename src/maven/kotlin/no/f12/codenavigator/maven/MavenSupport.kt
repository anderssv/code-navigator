package no.f12.codenavigator.maven

import no.f12.codenavigator.navigation.core.SourceSet
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
