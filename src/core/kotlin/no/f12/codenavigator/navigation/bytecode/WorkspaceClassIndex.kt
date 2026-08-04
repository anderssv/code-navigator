package no.f12.codenavigator.navigation.bytecode

import no.f12.codenavigator.navigation.types.AnalysisWorkspace
import no.f12.codenavigator.navigation.types.ClassName

/** Builds class-to-module provenance before class directories are flattened for analysis. */
fun AnalysisWorkspace.modulesOfClass(): Map<ClassName, Set<String>> {
    if (!moduleAware) return emptyMap()
    val result = mutableMapOf<ClassName, MutableSet<String>>()
    for (module in modules) {
        for (taggedDir in module.classDirectories) {
            if (!taggedDir.directory.exists()) continue
            scanProjectClasses(listOf(taggedDir.directory)).forEach { className ->
                result.getOrPut(className) { linkedSetOf() } += taggedDir.moduleId.value
            }
        }
    }
    return result.mapValues { it.value.toSet() }
}
