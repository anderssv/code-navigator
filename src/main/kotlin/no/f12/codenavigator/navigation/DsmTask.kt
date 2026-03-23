package no.f12.codenavigator.navigation

import no.f12.codenavigator.JsonFormatter
import no.f12.codenavigator.LlmFormatter
import no.f12.codenavigator.OutputFormat
import no.f12.codenavigator.OutputWrapper
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.File

@DisableCachingByDefault(because = "Produces console output only")
abstract class DsmTask : DefaultTask() {

    @TaskAction
    fun showDsm() {
        val rootPackage = project.findProperty("root-package")?.toString() ?: ""
        val depth = project.findProperty("depth")?.toString()?.toIntOrNull() ?: 2
        val htmlPath = project.findProperty("dsm-html")?.toString()
        val format = OutputFormat.from(project)

        val sourceSets = project.extensions.getByType(SourceSetContainer::class.java)
        val mainSourceSet = sourceSets.getByName("main")
        val classDirectories = mainSourceSet.output.classesDirs.files.toList()

        val dependencies = DsmDependencyExtractor.extract(classDirectories, rootPackage)
        val matrix = DsmMatrixBuilder.build(dependencies, rootPackage, depth)

        val output = when (format) {
            OutputFormat.TEXT -> DsmFormatter.format(matrix)
            OutputFormat.JSON -> JsonFormatter.formatDsm(matrix)
            OutputFormat.LLM -> LlmFormatter.formatDsm(matrix)
        }
        logger.lifecycle(OutputWrapper.wrap(output, format))

        if (htmlPath != null) {
            val htmlFile = File(htmlPath)
            htmlFile.parentFile?.mkdirs()
            htmlFile.writeText(DsmHtmlRenderer.render(matrix))
            logger.lifecycle("DSM HTML written to: ${htmlFile.absolutePath}")
        }
    }
}
