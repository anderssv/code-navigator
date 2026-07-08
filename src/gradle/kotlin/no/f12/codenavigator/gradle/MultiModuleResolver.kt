package no.f12.codenavigator.gradle

import no.f12.codenavigator.navigation.types.ModuleSourceSet
import no.f12.codenavigator.navigation.types.SourceSet
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.tasks.SourceSetContainer
import java.io.File

/**
 * How a module relates to the project the task was invoked on, for `--multi-module` aggregation.
 *
 * SOURCE isn't a single project — it's the transitive subtree rooted at the invoked project.
 * Invoked on a leaf, SOURCE is just that project. Invoked on an aggregator/root, SOURCE is the
 * root plus everything beneath it, collapsed into one scope (an aggregator has no code of its
 * own, so "analyze this project" means "analyze everything in this tree").
 */
enum class ModuleRelationship {
    /** The invoked project, or one of its transitive subprojects. */
    SOURCE,

    /** Reachable via an actual `project(":x")` dependency edge from something in SOURCE, walked transitively. */
    DEPENDENCY,

    /** Structurally related (parent, or a sibling under the same parent) but not an actual dependency. Excluded by default. */
    HIERARCHY,
}

/**
 * Aggregates tagged class directories across the modules actually related to the invoked project
 * (its own subtree plus real project dependencies), for `--multi-module` analysis. Module name is
 * the Gradle project path (e.g. ":shared") since it's unique across the build, unlike the bare
 * project name.
 */
object MultiModuleResolver {

    fun resolve(project: Project): List<Pair<File, ModuleSourceSet>> =
        includedModules(project).flatMap { taggedDirectoriesForModule(it) }

    /** Source directories (main + test) across every included module — for staleness checking, not analysis. */
    fun sourceDirectories(project: Project): List<File> =
        includedModules(project).flatMap { module ->
            val sourceSets = module.extensions.findByType(SourceSetContainer::class.java) ?: return@flatMap emptyList()
            val mainDirs = sourceSets.findByName("main")?.allSource?.srcDirs.orEmpty()
            val testDirs = sourceSets.findByName("test")?.allSource?.srcDirs.orEmpty()
            (mainDirs + testDirs).toList()
        }

    private fun includedModules(project: Project): Set<Project> =
        classify(project).filterValues { it != ModuleRelationship.HIERARCHY }.keys

    /** Classifies every project in the build relative to the invoked project. */
    fun classify(project: Project): Map<Project, ModuleRelationship> {
        val root = project.rootProject
        val sourceProjects = project.allprojects
        val dependencyProjects = transitiveProjectDependenciesOf(sourceProjects, root) - sourceProjects
        val hierarchyProjects = root.allprojects - sourceProjects - dependencyProjects

        return buildMap {
            sourceProjects.forEach { put(it, ModuleRelationship.SOURCE) }
            dependencyProjects.forEach { put(it, ModuleRelationship.DEPENDENCY) }
            hierarchyProjects.forEach { put(it, ModuleRelationship.HIERARCHY) }
        }
    }

    private fun transitiveProjectDependenciesOf(seed: Set<Project>, root: Project): Set<Project> {
        val visited = seed.toMutableSet()
        val queue = ArrayDeque(seed)
        val discovered = mutableSetOf<Project>()

        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            for (dep in directProjectDependenciesOf(current, root)) {
                if (visited.add(dep)) {
                    discovered += dep
                    queue += dep
                }
            }
        }

        return discovered
    }

    private fun directProjectDependenciesOf(project: Project, root: Project): Set<Project> {
        val result = mutableSetOf<Project>()
        for (config in project.configurations) {
            for (dep in config.dependencies.withType(ProjectDependency::class.java)) {
                runCatching { root.project(dep.path) }.getOrNull()?.let { result += it }
            }
        }
        return result
    }

    private fun taggedDirectoriesForModule(module: Project): List<Pair<File, ModuleSourceSet>> {
        val sourceSets = module.extensions.findByType(SourceSetContainer::class.java) ?: return emptyList()
        val moduleName = module.path
        val result = mutableListOf<Pair<File, ModuleSourceSet>>()

        sourceSets.findByName("main")?.output?.classesDirs?.files?.forEach { dir ->
            result.add(dir to ModuleSourceSet(moduleName, SourceSet.MAIN))
        }

        sourceSets.findByName("test")?.output?.classesDirs?.files
            ?.filter { it.exists() }
            ?.forEach { dir -> result.add(dir to ModuleSourceSet(moduleName, SourceSet.TEST)) }

        return result
    }
}
