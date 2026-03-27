package no.f12.codenavigator.gradle

import no.f12.codenavigator.AgentHelpText
import no.f12.codenavigator.BuildTool
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "Produces console output only")
abstract class AgentHelpTask : DefaultTask() {

    @TaskAction
    fun showAgentHelp() {
        val section = project.findProperty("section")?.toString()
        logger.lifecycle(AgentHelpText.generate(BuildTool.GRADLE, section))
    }
}
