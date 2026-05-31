package no.f12.codenavigator.navigation.refactor

import java.io.File

data class RenameChange(
    val filePath: String,
    val before: String,
    val after: String,
)

data class CascadeCandidate(
    val className: String,
    val methodName: String,
    val paramName: String,
)

data class RenameResult(
    val changes: List<RenameChange>,
    val cascadeCandidates: List<CascadeCandidate> = emptyList(),
    val warnings: List<String> = emptyList(),
) {
    fun toJson(): String {
        val cascadeJson = if (cascadeCandidates.isNotEmpty()) {
            val candidates = cascadeCandidates.joinToString(",", "[", "]") { c ->
                """{"className":"${jsonEscape(c.className)}","methodName":"${jsonEscape(c.methodName)}","paramName":"${jsonEscape(c.paramName)}"}"""
            }
            ""","cascadeCandidates":$candidates"""
        } else {
            ""
        }
        val warningsJson = if (warnings.isNotEmpty()) {
            val items = warnings.joinToString(",", "[", "]") { "\"${jsonEscape(it)}\"" }
            ""","warnings":$items"""
        } else {
            ""
        }
        return """{"changes":${changesToJson(changes)}$cascadeJson$warningsJson}"""
    }

    companion object {
        fun fromJson(json: String): RenameResult {
            val obj = parseJsonObject(json)
            val changes = changesFromJson(obj)
            val cascadeArr = obj["cascadeCandidates"] as? List<*> ?: emptyList<Any>()
            val cascadeCandidates = cascadeArr.map { item ->
                @Suppress("UNCHECKED_CAST")
                val map = item as Map<String, Any?>
                CascadeCandidate(
                    className = map["className"] as String,
                    methodName = map["methodName"] as String,
                    paramName = map["paramName"] as String,
                )
            }
            val warningsArr = obj["warnings"] as? List<*> ?: emptyList<Any>()
            val warnings = warningsArr.map { it as String }
            return RenameResult(changes, cascadeCandidates, warnings)
        }
    }
}

object RenameParamRewriter {

    private val CONSTRUCTOR_METHOD_NAMES = setOf("<init>", "<constructor>")

    fun rename(
        sourceRoots: List<File>,
        className: String,
        methodName: String,
        paramName: String,
        newName: String,
        preview: Boolean = false,
    ): RenameResult {
        return PsiRenameParamRewriter.rename(
            sourceRoots = sourceRoots,
            className = className,
            methodName = methodName,
            paramName = paramName,
            newName = newName,
            preview = preview,
        )
    }
}
