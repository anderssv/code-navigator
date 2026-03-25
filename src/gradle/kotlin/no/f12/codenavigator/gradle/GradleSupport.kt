package no.f12.codenavigator.gradle

import org.gradle.api.Project

fun Project.codeNavigatorExtension(): CodeNavigatorExtension =
    extensions.getByType(CodeNavigatorExtension::class.java)

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
