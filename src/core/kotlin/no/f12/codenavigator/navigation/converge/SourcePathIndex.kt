package no.f12.codenavigator.navigation.converge

import no.f12.codenavigator.navigation.classinfo.ClassInfo
import no.f12.codenavigator.navigation.types.PackageName

/**
 * Resolves a git-relative file path (e.g. "src/main/kotlin/com/example/Foo.kt") to the project class
 * it contains, bridging cnavCoupling's path-based git data with the package/class-based structural
 * data from cnavCycles/cnavRings/cnavComplexity. Built from each class's bytecode-derived
 * [ClassInfo.reconstructedSourcePath] (package-dir/filename, no source-root prefix) — a git path
 * resolves to a class if it *ends with* that suffix, since the git path additionally carries
 * whatever source root (src/main/kotlin, src/main/java, ...) the reconstructed path doesn't know about.
 *
 * Indexed by filename first so lookup doesn't scan every class for every coupling pair; multiple
 * classes can share a filename (multi-class files) but they're always in the same directory, so they
 * resolve to the same package regardless of which one the suffix match lands on.
 */
class SourcePathIndex private constructor(
    private val byFileName: Map<String, List<ClassInfo>>,
) {
    fun resolveClass(gitPath: String): ClassInfo? {
        val fileName = gitPath.substringAfterLast('/')
        val candidates = byFileName[fileName] ?: return null
        return candidates.firstOrNull { gitPath.endsWith(it.reconstructedSourcePath) }
    }

    fun resolvePackage(gitPath: String): PackageName? = resolveClass(gitPath)?.className?.packageName()

    companion object {
        fun from(classInfos: List<ClassInfo>): SourcePathIndex {
            val index = classInfos
                .filter { it.reconstructedSourcePath != "<unknown>" }
                .groupBy { it.reconstructedSourcePath.substringAfterLast('/') }
            return SourcePathIndex(index)
        }
    }
}
