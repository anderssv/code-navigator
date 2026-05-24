package no.f12.codenavigator.gradle

import no.f12.codenavigator.formatting.OutputWrapper
import no.f12.codenavigator.registry.ParamDef
import no.f12.codenavigator.navigation.bytecode.SkippedFileReporter
import no.f12.codenavigator.navigation.bytecode.scanProjectClasses
import no.f12.codenavigator.navigation.dsm.DsmDependencyExtractor
import no.f12.codenavigator.navigation.dsm.RingDetector
import no.f12.codenavigator.navigation.dsm.RingFormatter
import no.f12.codenavigator.registry.TaskRegistry
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.File

@DisableCachingByDefault(because = "Produces console output only")
abstract class RingsTask : DefaultTask() {

    @TaskAction
    fun detectRings() {
        val extension = project.codeNavigatorExtension()
        val cliProps = project.buildPropertyMap(TaskRegistry.RINGS)
        val props = extension.resolveProperties(cliProps)

        val format = ParamDef.parseFormat(props)

        val classDirectories = project.taggedClassDirectories().map { it.first }
        val projectClasses = scanProjectClasses(classDirectories)

        val extractResult = DsmDependencyExtractor.extract(classDirectories, projectClasses, packageFilter = null, includeExternal = false, filterTargets = true)
        val reportFile = File(project.layout.buildDirectory.asFile.get(), "cnav/skipped-files.txt")
        SkippedFileReporter.report(extractResult.skippedFiles, reportFile)?.let { logger.warn(it) }
        val dependencies = extractResult.data

        val result = RingDetector.detect(dependencies)
        val output = RingFormatter.format(result)

        logger.lifecycle(OutputWrapper.formatAndWrap(format,
            text = { output },
            json = { output },
            llm = { output },
        ))
    }
}
