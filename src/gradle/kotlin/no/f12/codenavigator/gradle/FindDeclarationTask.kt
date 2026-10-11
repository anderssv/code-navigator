package no.f12.codenavigator.gradle

import no.f12.codenavigator.formatting.OutputWrapper
import no.f12.codenavigator.navigation.declaration.DeclarationFilter
import no.f12.codenavigator.navigation.declaration.DeclarationFormatter
import no.f12.codenavigator.navigation.declaration.DeclarationInfo
import no.f12.codenavigator.navigation.declaration.FindDeclarationConfig
import no.f12.codenavigator.registry.BuildTool
import no.f12.codenavigator.registry.TaskRegistry
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import org.gradle.work.DisableCachingByDefault
import org.gradle.workers.WorkerExecutor
import javax.inject.Inject

@DisableCachingByDefault(because = "Produces console output only")
abstract class FindDeclarationTask @Inject constructor(
    private val workerExecutor: WorkerExecutor,
) : CodeNavigatorTask() {

    @get:Classpath
    abstract val psiClasspath: ConfigurableFileCollection

    @Option(option = "pattern", description = "Declaration name regex (camelCase-aware). Qualified patterns (containing '.') match the full dotted name or source path")
    @get:Internal
    var pattern: String? = null

    @Option(option = "kind", description = "Restrict to a declaration kind: all (default), class, function, property, typealias")
    @get:Internal
    var kind: String? = null

    @Option(option = "scope", description = "Filter by source set: all (default), prod (production only), test (test only)")
    @get:Internal
    var scope: String? = null

    override fun taskOptionsMap(): Map<String, String?> = buildMap {
        pattern?.let { put("pattern", it) }
        kind?.let { put("kind", it) }
        scope?.let { put("scope", it) }
    }

    @TaskAction
    fun findDeclaration() {
        val config = try {
            FindDeclarationConfig.parse(TaskRegistry.FIND_DECLARATION.enhanceProperties(buildOptionsMap()))
        } catch (e: IllegalArgumentException) {
            throw GradleException(
                "${e.message}\n${TaskRegistry.FIND_DECLARATION.usageHint(BuildTool.GRADLE)}\n" +
                    TaskRegistry.FIND_DECLARATION.renderExamples(BuildTool.GRADLE).joinToString("\n"),
            )
        }

        val sourceRoots = project.taggedSourceDirectories()
            .filter { (_, sourceSet) -> config.scope.matchesSourceSet(sourceSet) }
            .map { (dir, _) -> dir.absolutePath }
        val resultFileLocation = temporaryDir.resolve("find-declaration-result.json")

        val workQueue = workerExecutor.classLoaderIsolation { classpath.from(psiClasspath) }
        workQueue.submit(FindDeclarationWorkAction::class.java) {
            this.sourceRoots.set(sourceRoots)
            resultFile.set(resultFileLocation)
        }
        workQueue.await()

        val all = DeclarationInfo.listFromJson(resultFileLocation.readText())
        val matches = DeclarationFilter.filter(all, config.pattern, config.kind)
        if (matches.isEmpty()) {
            logger.quiet(OutputWrapper.emptyResult(config.format, "No declarations matching '${config.pattern}' found.", noResultsHints(config)))
            return
        }
        logger.quiet(OutputWrapper.formatAndWrap(config.format) { format -> DeclarationFormatter.format(matches, format) })
    }

    private fun noResultsHints(config: FindDeclarationConfig): List<String> = buildList {
        add("Unqualified patterns match the declaration's own name; use Class.member or a package prefix to match the full dotted name.")
        if (config.kind != null) add("Drop --kind to search all declaration kinds.")
        add("Only Kotlin (.kt) sources are scanned.")
    }
}
