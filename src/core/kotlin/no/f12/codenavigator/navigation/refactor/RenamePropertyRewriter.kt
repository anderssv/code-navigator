package no.f12.codenavigator.navigation.refactor

import java.io.File

data class RenamePropertyResult(
    val changes: List<RenameChange>,
) {
    fun toJson(): String = """{"changes":${changesToJson(changes)}}"""

    companion object {
        fun fromJson(json: String): RenamePropertyResult {
            val obj = parseJsonObject(json)
            return RenamePropertyResult(changesFromJson(obj))
        }
    }
}

object RenamePropertyRewriter {

    fun rename(
        sourceRoots: List<File>,
        className: String,
        propertyName: String,
        newName: String,
        preview: Boolean = false,
    ): RenamePropertyResult {
        return PsiRenamePropertyRewriter.rename(
            sourceRoots = sourceRoots,
            className = className,
            propertyName = propertyName,
            newName = newName,
            preview = preview,
        )
    }
}
