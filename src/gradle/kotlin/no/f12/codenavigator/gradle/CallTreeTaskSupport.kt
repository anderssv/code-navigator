package no.f12.codenavigator.gradle

import no.f12.codenavigator.config.OutputFormat

import no.f12.codenavigator.registry.BuildTool
import no.f12.codenavigator.formatting.JsonFormatter
import no.f12.codenavigator.formatting.LlmFormatter
import no.f12.codenavigator.formatting.OutputWrapper
import no.f12.codenavigator.registry.TaskDef
import no.f12.codenavigator.navigation.relations.callgraph.CallDirection
import no.f12.codenavigator.navigation.relations.callgraph.CallGraphConfig
import no.f12.codenavigator.navigation.relations.callgraph.CallTreeFormatter
import no.f12.codenavigator.navigation.relations.callgraph.CallTreeOrchestrator
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.logging.Logger
import java.io.File

object CallTreeTaskSupport {

    fun execute(
        project: Project,
        logger: Logger,
        taskDef: TaskDef,
        direction: CallDirection,
        properties: Map<String, String?>,
    ) {
        taskDef.deprecations(properties).forEach { logger.warn(it) }
        val config = try {
            CallGraphConfig.parse(properties)
        } catch (e: IllegalArgumentException) {
            throw GradleException("${e.message}\n${taskDef.usageHint(BuildTool.GRADLE)}")
        }

        val taggedDirs = project.taggedClassDirectories()
        val cacheDir = File(project.layout.buildDirectory.asFile.get(), "cnav")
        val output = CallTreeOrchestrator.run(config, taggedDirs, cacheDir, direction)

        output.skippedFileWarning?.let { logger.warn(it) }

        if (output.trees.isEmpty()) {
            logger.lifecycle(OutputWrapper.emptyResult(config.format, "No methods found matching '${config.method}'"))
            return
        }

        logger.lifecycle(
            OutputWrapper.formatAndWrap(config.format) { format ->
                when (format) {
                    OutputFormat.TEXT, OutputFormat.DIFF -> CallTreeFormatter.renderTrees(output.trees, direction) + (output.classHint?.let { "\n\n$it" } ?: "")
                    OutputFormat.JSON -> JsonFormatter.renderCallTrees(output.trees, direction)
                    OutputFormat.LLM -> LlmFormatter.renderCallTrees(output.trees, direction) + (output.classHint?.let { "\n\n$it" } ?: "")
                }
            },
        )
    }
}
