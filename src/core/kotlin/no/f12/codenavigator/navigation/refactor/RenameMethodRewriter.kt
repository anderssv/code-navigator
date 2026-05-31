package no.f12.codenavigator.navigation.refactor

import java.io.File

data class RenameMethodResult(
    val changes: List<RenameChange>,
) {
    fun toJson(): String = """{"changes":${changesToJson(changes)}}"""

    companion object {
        fun fromJson(json: String): RenameMethodResult {
            val obj = parseJsonObject(json)
            return RenameMethodResult(changesFromJson(obj))
        }
    }
}

object RenameMethodRewriter {

    fun rename(
        sourceRoots: List<File>,
        className: String,
        methodName: String,
        newName: String,
        preview: Boolean = false,
    ): RenameMethodResult = PsiRenameMethodRewriter.rename(
        sourceRoots = sourceRoots,
        className = className,
        methodName = methodName,
        newName = newName,
        preview = preview,
    )
}
