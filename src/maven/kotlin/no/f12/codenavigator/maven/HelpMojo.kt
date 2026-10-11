package no.f12.codenavigator.maven

import no.f12.codenavigator.HelpText
import no.f12.codenavigator.registry.BuildTool
import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter

@Mojo(name = "help")
class HelpMojo : AbstractMojo() {

    @Parameter(property = "task")
    private var task: String? = null

    override fun execute() {
        println(HelpText.generate(BuildTool.MAVEN, task))
    }
}
