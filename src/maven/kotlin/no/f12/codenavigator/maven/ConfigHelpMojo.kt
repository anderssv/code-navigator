package no.f12.codenavigator.maven

import no.f12.codenavigator.ConfigHelpText
import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugins.annotations.Mojo

@Mojo(name = "config-help")
class ConfigHelpMojo : AbstractMojo() {

    override fun execute() {
        println(ConfigHelpText.generate())
    }
}
