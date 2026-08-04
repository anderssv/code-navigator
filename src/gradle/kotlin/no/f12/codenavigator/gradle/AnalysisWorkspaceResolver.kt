package no.f12.codenavigator.gradle

import no.f12.codenavigator.navigation.types.AnalysisWorkspace
import no.f12.codenavigator.navigation.types.ModuleId
import no.f12.codenavigator.navigation.types.ModuleNode
import no.f12.codenavigator.navigation.types.ModuleRole
import no.f12.codenavigator.navigation.types.SourceSet
import no.f12.codenavigator.navigation.types.TaggedClassDirectory
import no.f12.codenavigator.navigation.types.TaggedSourceDirectory
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.tasks.SourceSetContainer

/** Resolves build-tool project structure once, before any analysis orchestrator runs. */
object AnalysisWorkspaceResolver {

    fun resolve(project: Project): AnalysisWorkspace {
        val classified = MultiModuleResolver.classify(project)
            .filterValues { it != ModuleRelationship.HIERARCHY }
        val included = classified.keys

        val modules = included.map { module ->
            val id = ModuleId(module.path)
            val parentId = module.parent
                ?.takeIf { it in included }
                ?.let { ModuleId(it.path) }
            val dependencies = directProjectDependencies(module)
                .filterTo(linkedSetOf()) { it in included }
                .mapTo(linkedSetOf()) { ModuleId(it.path) }
            val sourceSets = module.extensions.findByType(SourceSetContainer::class.java)

            ModuleNode(
                id = id,
                role = when (classified.getValue(module)) {
                    ModuleRelationship.SOURCE -> ModuleRole.SOURCE
                    ModuleRelationship.DEPENDENCY -> ModuleRole.DEPENDENCY
                    ModuleRelationship.HIERARCHY -> error("HIERARCHY modules are excluded from workspaces")
                },
                parentId = parentId,
                dependencies = dependencies,
                classDirectories = classDirectories(sourceSets, id),
                sourceDirectories = sourceDirectories(sourceSets, id),
            )
        }

        return AnalysisWorkspace(modules.sortedBy { it.id.value }, moduleAware = modules.size > 1)
    }

    private fun classDirectories(sourceSets: SourceSetContainer?, moduleId: ModuleId): List<TaggedClassDirectory> {
        if (sourceSets == null) return emptyList()
        val result = mutableListOf<TaggedClassDirectory>()
        sourceSets.findByName("main")?.output?.classesDirs?.files?.forEach { dir ->
            result += TaggedClassDirectory(dir, moduleId, SourceSet.MAIN)
        }
        sourceSets.findByName("test")?.output?.classesDirs?.files
            ?.filter { it.exists() }
            ?.forEach { dir -> result += TaggedClassDirectory(dir, moduleId, SourceSet.TEST) }
        return result
    }

    private fun sourceDirectories(sourceSets: SourceSetContainer?, moduleId: ModuleId): List<TaggedSourceDirectory> {
        if (sourceSets == null) return emptyList()
        return buildList {
            sourceSets.findByName("main")?.allSource?.srcDirs?.forEach { dir ->
                add(TaggedSourceDirectory(dir, moduleId, SourceSet.MAIN))
            }
            sourceSets.findByName("test")?.allSource?.srcDirs?.forEach { dir ->
                add(TaggedSourceDirectory(dir, moduleId, SourceSet.TEST))
            }
        }
    }

    private fun directProjectDependencies(project: Project): Set<Project> =
        project.configurations.flatMapTo(linkedSetOf()) { configuration ->
            configuration.dependencies.withType(ProjectDependency::class.java).mapNotNull { dependency ->
                runCatching { project.rootProject.project(dependency.path) }.getOrNull()
            }
        }
}
