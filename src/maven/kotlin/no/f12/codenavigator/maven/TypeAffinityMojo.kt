package no.f12.codenavigator.maven

import no.f12.codenavigator.config.OutputFormat
import no.f12.codenavigator.formatting.OutputWrapper
import no.f12.codenavigator.navigation.bytecode.scanProjectClasses
import no.f12.codenavigator.navigation.dsm.DsmDependencyExtractor
import no.f12.codenavigator.navigation.dsm.TypeAffinityBuilder
import no.f12.codenavigator.navigation.dsm.TypeAffinityConfig
import no.f12.codenavigator.navigation.dsm.TypeAffinityFormatter
import no.f12.codenavigator.navigation.types.Scope
import no.f12.codenavigator.registry.TaskRegistry
import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugins.annotations.Execute
import org.apache.maven.plugins.annotations.LifecyclePhase
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.project.MavenProject

@Mojo(name = "type-affinity")
@Execute(phase = LifecyclePhase.COMPILE)
class TypeAffinityMojo : AbstractMojo() {

    @Parameter(defaultValue = "\${project}", readonly = true, required = true)
    private lateinit var project: MavenProject

    @Parameter(property = "format")
    private var format: String? = null

    @Parameter(property = "package")
    private var pkg: String? = null

    @Parameter(property = "threshold")
    private var threshold: String? = null

    @Parameter(property = "scope")
    private var scope: String? = null

    override fun execute() {
        project.checkStaleness(log)

        val config = TypeAffinityConfig.parse(TaskRegistry.TYPE_AFFINITY.enhanceProperties(buildPropertyMap()))
        val scopeVal = Scope.parse(buildPropertyMap()["scope"])

        val taggedDirs = project.taggedClassDirectories()
        val classDirectories = taggedDirs
            .filter { scopeVal.matchesSourceSet(it.second) }
            .map { it.first }
        val projectClasses = scanProjectClasses(classDirectories)

        val extractResult = DsmDependencyExtractor.extract(classDirectories, projectClasses, packageFilter = null, includeExternal = false, filterTargets = true)
        val result = TypeAffinityBuilder.analyze(extractResult.data, config.targetPackage, config.threshold)

        if (result.singleOwnerTypes.isEmpty() && result.sharedTypes.isEmpty()) {
            println(OutputWrapper.emptyResult(config.format, "No types found in package '${config.targetPackage}' with external consumers."))
            return
        }

        println(OutputWrapper.formatAndWrap(config.format) { format ->
            TypeAffinityFormatter.format(result, format)
        })
    }

    private fun buildPropertyMap(): Map<String, String?> = buildMap {
        format?.let { put("format", it) }
        pkg?.let { put("package", it) }
        threshold?.let { put("threshold", it) }
        scope?.let { put("scope", it) }
    }
}
