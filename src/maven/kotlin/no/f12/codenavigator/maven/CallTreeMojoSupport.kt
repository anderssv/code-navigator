package no.f12.codenavigator.maven

import no.f12.codenavigator.config.OutputFormat

import no.f12.codenavigator.formatting.OutputWrapper
import no.f12.codenavigator.registry.TaskDef
import no.f12.codenavigator.navigation.relations.callgraph.CallDirection
import no.f12.codenavigator.navigation.relations.callgraph.CallGraphConfig
import no.f12.codenavigator.navigation.relations.callgraph.CallTreeFormatter
import no.f12.codenavigator.navigation.relations.callgraph.CallTreeOrchestrator
import org.apache.maven.plugin.MojoFailureException
import org.apache.maven.plugin.logging.Log
import org.apache.maven.project.MavenProject
import java.io.File

object CallTreeMojoSupport {

    fun execute(
        project: MavenProject,
        log: Log,
        properties: Map<String, String?>,
        taskDef: TaskDef,
        direction: CallDirection,
        usageHint: String,
    ) {
        taskDef.deprecations(properties).forEach { log.warn(it) }
        val config = try {
            CallGraphConfig.parse(taskDef.enhanceProperties(project.applyConfigDefaults(properties)))
        } catch (e: IllegalArgumentException) {
            throw MojoFailureException(usageHint)
        }

        val taggedDirs = project.taggedClassDirectories()
        if (taggedDirs.isEmpty()) {
            log.warn("Classes directory does not exist: ${File(project.build.outputDirectory)} — run 'mvn compile' first.")
            return
        }

        val cacheDir = File(project.build.directory, "cnav")
        val output = CallTreeOrchestrator.run(config, taggedDirs, cacheDir, direction)

        output.skippedFileWarning?.let { log.warn(it) }

        if (output.trees.isEmpty()) {
            println(OutputWrapper.emptyResult(config.format, "No methods found matching '${config.method}'"))
            return
        }

        println(
            OutputWrapper.formatAndWrap(config.format) { format ->
                when (format) {
                    OutputFormat.TEXT, OutputFormat.DIFF -> CallTreeFormatter.renderTrees(output.trees, direction) + (output.classHint?.let { "\n\n$it" } ?: "")
                    OutputFormat.JSON -> CallTreeFormatter.formatJson(output.trees, direction)
                    OutputFormat.LLM -> CallTreeFormatter.formatLlm(output.trees, direction) + (output.classHint?.let { "\n\n$it" } ?: "")
                }
            },
        )
    }
}
