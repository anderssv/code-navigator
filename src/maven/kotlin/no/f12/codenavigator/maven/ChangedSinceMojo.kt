package no.f12.codenavigator.maven

import no.f12.codenavigator.JsonFormatter
import no.f12.codenavigator.LlmFormatter
import no.f12.codenavigator.OutputWrapper
import no.f12.codenavigator.registry.TaskRegistry
import no.f12.codenavigator.navigation.SourceSet
import no.f12.codenavigator.navigation.SourceSetResolver
import no.f12.codenavigator.navigation.callgraph.CallGraphCache
import no.f12.codenavigator.navigation.changedsince.ChangedSinceBuilder
import no.f12.codenavigator.navigation.changedsince.ChangedSinceConfig
import no.f12.codenavigator.navigation.changedsince.ChangedSinceFormatter
import no.f12.codenavigator.navigation.changedsince.GitDiffRunner
import no.f12.codenavigator.navigation.changedsince.SourceFileResolver
import no.f12.codenavigator.navigation.classinfo.ClassIndexCache
import no.f12.codenavigator.navigation.SkippedFileReporter
import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugins.annotations.Execute
import org.apache.maven.plugins.annotations.LifecyclePhase
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.project.MavenProject
import java.io.File

@Mojo(name = "changed-since")
@Execute(phase = LifecyclePhase.COMPILE)
class ChangedSinceMojo : AbstractMojo() {

    @Parameter(defaultValue = "\${project}", readonly = true, required = true)
    private lateinit var project: MavenProject

    @Parameter(property = "format")
    private var format: String? = null

    @Parameter(property = "llm")
    private var llm: String? = null

    @Parameter(property = "ref")
    private var ref: String? = null

    @Parameter(property = "project-only")
    private var projectOnly: String? = null

    @Parameter(property = "prod-only")
    private var prodOnly: String? = null

    @Parameter(property = "test-only")
    private var testOnly: String? = null

    override fun execute() {
        val config = ChangedSinceConfig.parse(TaskRegistry.CHANGED_SINCE.enhanceProperties(buildPropertyMap()))

        if (config.ref == null) {
            log.error("Required parameter 'ref' not set. Usage: -Dref=<git-ref> (branch, tag, or commit SHA)")
            return
        }

        val taggedDirs = project.taggedClassDirectories()
        val resolver = SourceSetResolver.from(taggedDirs)

        if (resolver.classDirectories.isEmpty() || resolver.classDirectories.none { it.exists() }) {
            log.warn("Classes directory does not exist — run 'mvn compile' first.")
            return
        }

        val gitPaths = GitDiffRunner.run(project.basedir, config.ref!!)
        if (gitPaths.isEmpty()) {
            println("No changed files since ${config.ref}.")
            return
        }

        val classInfos = ClassIndexCache.getOrBuild(
            File(project.build.directory, "cnav/class-index.cache"),
            resolver.classDirectories,
        ).data

        val resolution = SourceFileResolver.resolve(gitPaths, classInfos)

        if (resolution.resolved.isEmpty()) {
            if (resolution.unresolved.isNotEmpty()) {
                println("${resolution.unresolved.size} changed file(s), none mapped to project classes:")
                resolution.unresolved.forEach { println("  $it") }
            } else {
                println("No changed files since ${config.ref}.")
            }
            return
        }

        val result = CallGraphCache.getOrBuild(File(project.build.directory, "cnav/call-graph.cache"), resolver.classDirectories)
        val reportFile = File(project.build.directory, "cnav/skipped-files.txt")
        SkippedFileReporter.report(result.skippedFiles, reportFile)?.let { log.warn(it) }
        val graph = result.data

        val allImpacts = ChangedSinceBuilder.build(
            changedClasses = resolution.resolved.keys,
            graph = graph,
            projectOnly = config.projectOnly,
        )
        val impacts = when {
            config.prodOnly -> allImpacts.filter { resolver.sourceSetOf(it.className) == SourceSet.MAIN }
            config.testOnly -> allImpacts.filter { resolver.sourceSetOf(it.className) == SourceSet.TEST }
            else -> allImpacts
        }

        println(
            OutputWrapper.formatAndWrap(
                config.format,
                text = { ChangedSinceFormatter.format(impacts, resolution.unresolved) },
                json = { JsonFormatter.formatChangedSince(impacts, resolution.unresolved) },
                llm = { LlmFormatter.formatChangedSince(impacts, resolution.unresolved) },
            ),
        )
    }

    private fun buildPropertyMap(): Map<String, String?> = buildMap {
        format?.let { put("format", it) }
        llm?.let { put("llm", it) }
        ref?.let { put("ref", it) }
        projectOnly?.let { put("project-only", it) }
        prodOnly?.let { put("prod-only", it) }
        testOnly?.let { put("test-only", it) }
    }
}
