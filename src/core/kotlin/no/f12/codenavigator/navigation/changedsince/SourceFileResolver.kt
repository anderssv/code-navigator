package no.f12.codenavigator.navigation.changedsince

import no.f12.codenavigator.navigation.ClassName
import no.f12.codenavigator.navigation.classinfo.ClassInfo

data class ChangedFileResolution(
    val resolved: Map<ClassName, String>,
    val unresolved: List<String>,
)

object SourceFileResolver {

    fun resolve(
        gitPaths: List<String>,
        classInfos: List<ClassInfo>,
    ): ChangedFileResolution {
        val byReconstructedPath = classInfos.groupBy { it.reconstructedSourcePath }

        val resolved = mutableMapOf<ClassName, String>()
        val unresolved = mutableListOf<String>()

        for (gitPath in gitPaths) {
            val matched = findMatchingClasses(gitPath, byReconstructedPath)
            if (matched.isNotEmpty()) {
                for (classInfo in matched) {
                    resolved[classInfo.className] = gitPath
                }
            } else {
                unresolved.add(gitPath)
            }
        }

        return ChangedFileResolution(resolved, unresolved)
    }

    private fun findMatchingClasses(
        gitPath: String,
        byReconstructedPath: Map<String, List<ClassInfo>>,
    ): List<ClassInfo> {
        // Try suffix matching: strip known source root prefixes, then fall back to
        // matching the reconstructedSourcePath as a suffix of the git path
        for ((reconstructed, classInfos) in byReconstructedPath) {
            if (gitPath.endsWith("/$reconstructed") || gitPath == reconstructed) {
                return classInfos
            }
        }
        return emptyList()
    }
}
