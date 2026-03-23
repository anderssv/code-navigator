package no.f12.codenavigator.maven

import no.f12.codenavigator.AgentHelpText
import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugins.annotations.Mojo

@Mojo(name = "agent-help")
class AgentHelpMojo : AbstractMojo() {

    override fun execute() {
        println(AgentHelpText.generate())
    }
}
