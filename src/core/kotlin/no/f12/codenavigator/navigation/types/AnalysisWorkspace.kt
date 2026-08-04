package no.f12.codenavigator.navigation.types

import java.io.File

@JvmInline
value class ModuleId(val value: String) {
    override fun toString(): String = value
}

enum class ModuleRole {
    SOURCE,
    DEPENDENCY,
}

data class TaggedClassDirectory(
    val directory: File,
    val moduleId: ModuleId,
    val sourceSet: SourceSet,
)

data class TaggedSourceDirectory(
    val directory: File,
    val moduleId: ModuleId,
    val sourceSet: SourceSet,
)

data class ModuleNode(
    val id: ModuleId,
    val role: ModuleRole,
    val parentId: ModuleId? = null,
    val dependencies: Set<ModuleId> = emptySet(),
    val classDirectories: List<TaggedClassDirectory> = emptyList(),
    val sourceDirectories: List<TaggedSourceDirectory> = emptyList(),
)

data class AnalysisWorkspace(
    val modules: List<ModuleNode>,
    val classpath: List<File> = emptyList(),
    /** Whether outputs should retain/render module provenance. Single-module behavior stays unchanged when false. */
    val moduleAware: Boolean = false,
) {
    private val modulesById = modules.associateBy { it.id }

    fun module(id: ModuleId): ModuleNode? = modulesById[id]

    fun childrenOf(id: ModuleId): List<ModuleNode> = modules.filter { it.parentId == id }

    fun dependenciesOf(id: ModuleId): Set<ModuleNode> =
        module(id)?.dependencies.orEmpty().mapNotNullTo(linkedSetOf()) { modulesById[it] }

    fun taggedClassDirectories(): List<Pair<File, SourceSet>> =
        modules.flatMap { module -> module.classDirectories.map { it.directory to it.sourceSet } }

    fun classDirectories(scope: Scope): List<File> =
        modules.flatMap { module ->
            module.classDirectories
                .filter { scope.matchesSourceSet(it.sourceSet) }
                .map { it.directory }
        }

    fun moduleTaggedClassDirectories(): List<Pair<File, ModuleSourceSet>> =
        modules.flatMap { module ->
            module.classDirectories.map { it.directory to ModuleSourceSet(it.moduleId.value, it.sourceSet) }
        }

    fun sourceDirectories(scope: Scope = Scope.ALL): List<File> =
        modules.flatMap { module ->
            module.sourceDirectories
                .filter { scope.matchesSourceSet(it.sourceSet) }
                .map { it.directory }
        }
}
