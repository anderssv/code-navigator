package no.f12.codenavigator.gradle

import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency

/**
 * How a module relates to the project the task was invoked on during workspace resolution.
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

/** Classifies the Gradle project graph; [AnalysisWorkspaceResolver] owns directory resolution. */
object MultiModuleResolver {

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
}
