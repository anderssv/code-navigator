package no.f12.codenavigator.maven

import no.f12.codenavigator.config.OutputFormat
import no.f12.codenavigator.formatting.OutputWrapper
import no.f12.codenavigator.navigation.bytecode.scanProjectClasses
import no.f12.codenavigator.navigation.refactor.ExecutePlanFormatter
import no.f12.codenavigator.navigation.refactor.ExecutePlanResult
import no.f12.codenavigator.navigation.refactor.ExecutePlanStepResult
import no.f12.codenavigator.navigation.refactor.MoveClassRewriter
import no.f12.codenavigator.navigation.refactor.MovePackageConfig
import no.f12.codenavigator.navigation.types.ClassName
import no.f12.codenavigator.registry.BuildTool
import no.f12.codenavigator.registry.TaskRegistry
import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.project.MavenProject
import java.io.File

@Mojo(name = "move-package")
open class MovePackageMojo : AbstractMojo() {

    @Parameter(defaultValue = "\${project}", readonly = true, required = true)
    protected lateinit var project: MavenProject

    @Parameter(property = "format")
    protected var format: String? = null

    @Parameter(property = "from-package")
    protected var fromPackage: String? = null

    @Parameter(property = "to-package")
    protected var toPackage: String? = null

    @Parameter(property = "preview")
    protected var preview: String? = null

    override fun execute() {
        val config = try {
            MovePackageConfig.parse(TaskRegistry.MOVE_PACKAGE_TASK.enhanceProperties(project.applyConfigDefaults(buildPropertyMap())))
        } catch (e: IllegalArgumentException) {
            println(OutputWrapper.emptyResult(OutputFormat.LLM, "move-package failed: ${e.message}",
                TaskRegistry.MOVE_PACKAGE_TASK.renderExamples(BuildTool.MAVEN)))
            return
        }

        val classesRoots = listOfNotNull(
            File(project.build.outputDirectory).takeIf { it.exists() },
            File(project.build.testOutputDirectory).takeIf { it.exists() },
        )
        val classesInPackage = scanProjectClasses(classesRoots)
            .filter { ClassName(it.value).packageName().value == config.fromPackage }
            .map { it.value }

        if (classesInPackage.isEmpty()) {
            println(OutputWrapper.emptyResult(config.format, "No classes found in package '${config.fromPackage}'.", noResultsHints(config)))
            return
        }

        val sourceRoots = (project.compileSourceRoots + project.testCompileSourceRoots)
            .map { root -> File(root as String) }
            .filter { it.exists() }

        val classpath = classesRoots.map { it.toPath() }

        val stepResults = mutableListOf<ExecutePlanStepResult>()

        for (fqcn in classesInPackage) {
            val simpleName = ClassName(fqcn).simpleName()
            val to = "${config.toPackage}.$simpleName"

            val result = MoveClassRewriter.move(
                sourceRoots = sourceRoots,
                className = fqcn,
                newFqcn = to,
                classpath = classpath,
                preview = config.preview,
            )

            stepResults.add(ExecutePlanStepResult(from = fqcn, to = to, result = result))
        }

        val planResult = ExecutePlanResult(steps = stepResults, preview = config.preview)

        if (planResult.totalChanges == 0) {
            println(OutputWrapper.emptyResult(config.format, "No changes needed for any class in package.", noResultsHints(config)))
            return
        }

        println(OutputWrapper.formatAndWrap(config.format) { format ->
            ExecutePlanFormatter.format(planResult, format)
        })
    }

    private fun noResultsHints(config: MovePackageConfig): List<String> = buildList {
        add("Ensure the package exists and contains at least one class.")
        add("Package names are dot-separated (e.g., com.example.services).")
        if (!config.preview) add("Try with -Dpreview=true first to inspect changes before applying.")
    }

    private fun buildPropertyMap(): Map<String, String?> = buildMap {
        format?.let { put("format", it) }
        fromPackage?.let { put("from-package", it) }
        toPackage?.let { put("to-package", it) }
        preview?.let { put("preview", it) }
    }
}
