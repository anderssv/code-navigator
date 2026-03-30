package no.f12.codenavigator.gradle

import no.f12.codenavigator.TaskRegistry
import no.f12.codenavigator.navigation.callgraph.CallDirection
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "Produces console output only")
abstract class FindCallersTask : DefaultTask() {

    @TaskAction
    fun findCallers() {
        CallTreeTaskSupport.execute(
            project = project,
            logger = logger,
            taskDef = TaskRegistry.FIND_CALLERS,
            direction = CallDirection.CALLERS,
            usageHint = "Missing required property. Usage: ./gradlew cnavCallers -Ppattern=<regex> -Pmaxdepth=3",
        )
    }
}
