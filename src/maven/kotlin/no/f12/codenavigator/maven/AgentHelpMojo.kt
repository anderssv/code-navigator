package no.f12.codenavigator.maven

import no.f12.codenavigator.AgentHelpText
import no.f12.codenavigator.registry.BuildTool
import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter

@Mojo(name = "agent-help")
class AgentHelpMojo : AbstractMojo() {

    @Parameter(property = "section")
    private var section: String? = null

    override fun execute() {
        println(AgentHelpText.generate(BuildTool.MAVEN, section))
    }
}
