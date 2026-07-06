package no.f12.codenavigator.maven

import no.f12.codenavigator.formatting.DsmOutputFormatter
import no.f12.codenavigator.registry.TaskRegistry
import no.f12.codenavigator.navigation.dsm.MoveSuggestConfig
import no.f12.codenavigator.navigation.dsm.MoveSuggestOrchestrator
import no.f12.codenavigator.navigation.dsm.PackageHealthExtractor
import no.f12.codenavigator.navigation.dsm.PlanMutator
import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugins.annotations.Execute
import org.apache.maven.plugins.annotations.LifecyclePhase
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.project.MavenProject
import java.io.File

@Mojo(name = "move-suggest")
@Execute(phase = LifecyclePhase.COMPILE)
class MoveSuggestMojo : AbstractMojo() {

    @Parameter(defaultValue = "\${project}", readonly = true, required = true)
    private lateinit var project: MavenProject

    @Parameter(property = "format")
    private var format: String? = null


    @Parameter(property = "package-filter")
    private var packageFilter: String? = null

    @Parameter(property = "top")
    private var top: String? = null

    @Parameter(property = "max-fan-in")
    private var maxFanIn: String? = null

    @Parameter(property = "scope")
    private var scope: String? = null

    @Parameter(property = "plan-file")
    private var planFile: String? = null

    override fun execute() {
        project.checkStaleness(log)

        val config = MoveSuggestConfig.parse(TaskRegistry.MOVE_SUGGEST.enhanceProperties(project.applyConfigDefaults(buildPropertyMap())))

        val taggedDirs = project.taggedClassDirectories()
        val filteredDirs = taggedDirs.filter { config.scope.matchesSourceSet(it.second) }
        val classDirectories = filteredDirs.map { it.first }

        if (classDirectories.isEmpty() || classDirectories.none { it.exists() }) {
            log.warn("Classes directory does not exist — run 'mvn compile' first.")
            return
        }

        val reportFile = File(project.build.directory, "cnav/skipped-files.txt")
        val extraction = PackageHealthExtractor.extract(classDirectories, config.packageFilter, reportFile)
        val plan = loadPlanSteps(planFile)
        if (plan.isNotEmpty()) {
            log.info("Applying plan: ${plan.size} step(s) from $planFile")
        }
        val mutatedExtraction = extraction.copy(
            dependencies = PlanMutator.apply(extraction.dependencies, plan, dropSamePackageEdges = false),
            projectClasses = PlanMutator.applyToClassSet(extraction.projectClasses, plan),
        )
        val output = MoveSuggestOrchestrator.fromExtraction(mutatedExtraction, config)

        output.skippedFileWarning?.let { log.warn(it) }
        DsmOutputFormatter.format(output, config.format)?.let { println(it) }
    }

    private fun buildPropertyMap(): Map<String, String?> = buildMap {
        format?.let { put("format", it) }
        packageFilter?.let { put("package-filter", it) }
        top?.let { put("top", it) }
        maxFanIn?.let { put("max-fan-in", it) }
        scope?.let { put("scope", it) }
        planFile?.let { put("plan-file", it) }
    }
}
