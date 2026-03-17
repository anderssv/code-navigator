package no.f12.codenavigator

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.File

@DisableCachingByDefault(because = "Produces console output only")
abstract class ListClassesTask : DefaultTask() {

    @TaskAction
    fun listClasses() {
        val sourceSets = project.extensions.getByType(SourceSetContainer::class.java)
        val mainSourceSet = sourceSets.getByName("main")
        val classDirectories = mainSourceSet.output.classesDirs.files.toList()
        val cacheFile = File(project.layout.buildDirectory.asFile.get(), "code-navigator/class-index.txt")

        val classes = loadClasses(cacheFile, classDirectories)
        val output = TableFormatter.format(classes)

        logger.lifecycle(output)
    }
}

internal fun loadClasses(cacheFile: File, classDirectories: List<File>): List<ClassInfo> {
    if (ClassIndexCache.isFresh(cacheFile, classDirectories)) {
        return ClassIndexCache.read(cacheFile)
    }

    val classes = ClassScanner.scan(classDirectories)
    ClassIndexCache.write(cacheFile, classes)
    return classes
}
