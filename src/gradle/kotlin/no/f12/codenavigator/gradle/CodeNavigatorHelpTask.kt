package no.f12.codenavigator.gradle

import no.f12.codenavigator.HelpText
import no.f12.codenavigator.registry.BuildTool
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "Produces console output only")
abstract class CodeNavigatorHelpTask : DefaultTask() {

    @Option(option = "task", description = "Full documentation for one task: parameters, examples, JSON schema, interpretation, caveats")
    @get:Internal
    var task: String? = null

    @TaskAction
    fun showHelp() {
        logger.quiet(HelpText.generate(BuildTool.GRADLE, task))
    }
}
