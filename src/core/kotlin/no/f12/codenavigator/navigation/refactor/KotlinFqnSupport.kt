package no.f12.codenavigator.navigation.refactor

import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFile

/** Builds the dotted FQN of [clazz] (walking enclosing classes for nested types), e.g. "pkg.Outer.Inner". */
internal fun buildClassFqn(filePackage: String, clazz: KtClass): String {
    val names = mutableListOf(clazz.name ?: "")
    var parent = clazz.parent
    while (parent != null) {
        if (parent is KtClass) {
            names.add(0, parent.name ?: "")
        }
        parent = parent.parent
    }
    val classPath = names.joinToString(".")
    return if (filePackage.isEmpty()) classPath else "$filePackage.$classPath"
}

/** True if [classFqn] is [targetFqn] itself or its synthetic Companion object. */
internal fun matchesFqn(classFqn: String, targetFqn: String): Boolean {
    if (classFqn == targetFqn) return true
    if (classFqn == "$targetFqn.Companion") return true
    return false
}

/** True if [ktFile] is in the same package as [targetFqn], or imports it (directly or via wildcard). */
internal fun fileReferencesClass(ktFile: KtFile, targetFqn: String): Boolean {
    val filePackage = ktFile.packageFqName.asString()
    val targetPackage = targetFqn.substringBeforeLast(".", "")
    if (filePackage == targetPackage) return true

    val imports = ktFile.importDirectives
    for (imp in imports) {
        val importedFqn = imp.importedFqName?.asString() ?: continue
        if (importedFqn == targetFqn) return true
        if (imp.isAllUnder && importedFqn == targetPackage) return true
    }
    return false
}
