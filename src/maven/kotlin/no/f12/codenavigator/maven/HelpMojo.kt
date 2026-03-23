package no.f12.codenavigator.maven

import no.f12.codenavigator.HelpText
import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugins.annotations.Mojo

@Mojo(name = "help")
class HelpMojo : AbstractMojo() {

    override fun execute() {
        println(HelpText.generate())
    }
}
