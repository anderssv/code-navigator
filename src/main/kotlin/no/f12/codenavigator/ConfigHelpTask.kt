package no.f12.codenavigator

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "Produces console output only")
abstract class ConfigHelpTask : DefaultTask() {

    @TaskAction
    fun showConfig() {
        logger.lifecycle(ConfigHelpText.generate())
    }
}
