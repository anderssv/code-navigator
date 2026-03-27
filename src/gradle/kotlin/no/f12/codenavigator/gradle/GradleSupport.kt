package no.f12.codenavigator.gradle

import no.f12.codenavigator.TaskDef
import org.gradle.api.Project

fun Project.codeNavigatorExtension(): CodeNavigatorExtension =
    extensions.getByType(CodeNavigatorExtension::class.java)

fun Project.buildPropertyMap(
    taskDef: TaskDef,
): Map<String, String?> {
    val propertyNames = taskDef.params.filter { !it.flag }.map { it.name }
    val flagNames = taskDef.params.filter { it.flag }.map { it.name }
    val raw = buildPropertyMap(propertyNames, flagNames)
    return taskDef.enhanceProperties(raw)
}

fun Project.buildPropertyMap(
    propertyNames: List<String>,
    flagNames: List<String>,
): Map<String, String?> {
    val map = mutableMapOf<String, String?>()
    for (name in propertyNames) {
        findProperty(name)?.let { map[name] = it.toString() }
    }
    for (name in flagNames) {
        if (hasProperty(name)) {
            map[name] = null
        }
    }
    return map
}
