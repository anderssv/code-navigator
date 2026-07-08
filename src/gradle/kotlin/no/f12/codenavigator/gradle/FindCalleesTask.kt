package no.f12.codenavigator.gradle

import no.f12.codenavigator.registry.TaskRegistry
import no.f12.codenavigator.navigation.relations.callgraph.CallDirection
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "Produces console output only")
abstract class FindCalleesTask : CodeNavigatorTask() {

    @Option(option = "pattern", description = "Class.method name regex (camelCase-aware: MyService.doWork matches com.example.MyService.doWork)")
    @get:Internal
    var pattern: String? = null

    @Option(option = "method", description = "Deprecated: use pattern instead")
    @get:Internal
    var method: String? = null

    @Option(option = "maxdepth", description = "Max call tree depth")
    @get:Internal
    var maxdepth: String? = null

    @Option(option = "project-only", description = "Hide JDK/stdlib/library classes (default: on)")
    @get:Internal
    var projectOnly: String? = null

    @Option(option = "filter-synthetic", description = "Set false to include synthetic methods (equals, hashCode, copy, componentN, etc.)")
    @get:Internal
    var filterSynthetic: String? = null

    @Option(option = "scope", description = "Filter by source set: all (default), prod (production only), test (test only)")
    @get:Internal
    var scope: String? = null

    @Option(option = "max-implementors", description = "Max polymorphic implementors to expand per interface call site before collapsing the rest into a '+N more' note")
    @get:Internal
    var maxImplementors: String? = null

    override fun taskOptionsMap(): Map<String, String?> = buildMap {
        pattern?.let { put("pattern", it) }
        method?.let { put("method", it) }
        maxdepth?.let { put("maxdepth", it) }
        projectOnly?.let { put("project-only", it) }
        filterSynthetic?.let { put("filter-synthetic", it) }
        scope?.let { put("scope", it) }
        maxImplementors?.let { put("max-implementors", it) }
    }

    @TaskAction
    fun findCallees() {
        CallTreeTaskSupport.execute(
            project = project,
            logger = logger,
            taskDef = TaskRegistry.FIND_CALLEES,
            direction = CallDirection.CALLEES,
            properties = TaskRegistry.FIND_CALLEES.enhanceProperties(buildOptionsMap()),
        )
    }
}
