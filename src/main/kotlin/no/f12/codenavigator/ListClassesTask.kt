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
        val jsonFormat = project.findProperty("format")?.toString() == "json"

        val sourceSets = project.extensions.getByType(SourceSetContainer::class.java)
        val mainSourceSet = sourceSets.getByName("main")
        val classDirectories = mainSourceSet.output.classesDirs.files.toList()
        val cacheFile = File(project.layout.buildDirectory.asFile.get(), "cnav/class-index.cache")

        val classes = ClassIndexCache.getOrScan(cacheFile, classDirectories)
        val output = if (jsonFormat) JsonFormatter.formatClasses(classes) else TableFormatter.format(classes)

        logger.lifecycle(output)
    }
}
