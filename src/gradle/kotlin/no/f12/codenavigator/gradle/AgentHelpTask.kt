package no.f12.codenavigator.gradle

import no.f12.codenavigator.AgentHelpText
import no.f12.codenavigator.registry.BuildTool
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "Produces console output only")
abstract class AgentHelpTask : CodeNavigatorTask() {

    @Option(option = "section", description = "Help section: install, setup, workflow, questions, interpretation, schemas, extraction, recommendations, refactor, getting-started, skill")
    @get:Internal
    var section: String? = null

    @Option(option = "topic", description = "Philosophy topic: hexagonal, tttd, fakes, manual-di")
    @get:Internal
    var topic: String? = null

    @Option(option = "task", description = "Full documentation for one task: parameters, examples, JSON schema, interpretation, caveats")
    @get:Internal
    var task: String? = null

    override fun taskOptionsMap(): Map<String, String?> = buildMap {
        section?.let { put("section", it) }
        topic?.let { put("topic", it) }
        task?.let { put("task", it) }
    }

    @TaskAction
    fun showAgentHelp() {
        logger.quiet(AgentHelpText.generate(BuildTool.GRADLE, section, topic, task))
    }
}
