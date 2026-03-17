package no.f12.codenavigator

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "Produces console output only")
abstract class CodeNavigatorHelpTask : DefaultTask() {

    @TaskAction
    fun showHelp() {
        logger.lifecycle(HelpText.generate())
    }
}
