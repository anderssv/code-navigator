package no.f12.codenavigator.navigation.declaration

import no.f12.codenavigator.navigation.refactor.jsonEscape
import no.f12.codenavigator.navigation.refactor.parseJsonObjectArray

/** Coarse grouping used by `--kind`; the fine-grained [DeclarationKind] is what gets reported. */
enum class DeclarationKindGroup { CLASS, FUNCTION, PROPERTY, TYPEALIAS;

    companion object {
        /** Parses `--kind`. `all`/null means no filter. Throws on unknown values so a typo doesn't silently return nothing. */
        fun parse(value: String?): DeclarationKindGroup? {
            if (value == null || value.equals("all", ignoreCase = true)) return null
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
                ?: throw IllegalArgumentException(
                    "Unknown kind '$value'. Expected one of: all, ${entries.joinToString(", ") { it.name.lowercase() }}",
                )
        }
    }
}

enum class DeclarationKind(val group: DeclarationKindGroup) {
    CLASS(DeclarationKindGroup.CLASS),
    INTERFACE(DeclarationKindGroup.CLASS),
    OBJECT(DeclarationKindGroup.CLASS),
    COMPANION_OBJECT(DeclarationKindGroup.CLASS),
    ENUM(DeclarationKindGroup.CLASS),
    ENUM_ENTRY(DeclarationKindGroup.CLASS),
    ANNOTATION(DeclarationKindGroup.CLASS),
    FUNCTION(DeclarationKindGroup.FUNCTION),
    CONSTRUCTOR(DeclarationKindGroup.FUNCTION),
    PROPERTY(DeclarationKindGroup.PROPERTY),
    TYPEALIAS(DeclarationKindGroup.TYPEALIAS),
    ;

    val label: String get() = name.lowercase()
}

/**
 * Exact source boundaries of one Kotlin declaration, as reported by the compiler's PSI.
 *
 * The range covers the *whole* declaration: leading KDoc and annotations, modifiers, the
 * signature and the body up to and including the closing brace (or the end of an expression body).
 * [nameLine] is where the identifier itself sits, which is usually what a reader wants to jump to.
 *
 * Lines and columns are 1-based. Offsets are 0-based character offsets into the file content with
 * line endings normalised to `\n`, [endOffset] exclusive — `content.substring(startOffset, endOffset)`
 * is exactly the declaration text.
 */
data class DeclarationInfo(
    val kind: DeclarationKind,
    val fqn: String,
    val name: String,
    val signature: String,
    val file: String,
    val sourcePath: String,
    val startLine: Int,
    val startColumn: Int,
    val endLine: Int,
    val endColumn: Int,
    val startOffset: Int,
    val endOffset: Int,
    val nameLine: Int,
) {
    fun toJson(): String = buildString {
        append('{')
        append("\"kind\":\"").append(kind.label).append("\",")
        append("\"fqn\":\"").append(jsonEscape(fqn)).append("\",")
        append("\"name\":\"").append(jsonEscape(name)).append("\",")
        append("\"signature\":\"").append(jsonEscape(signature)).append("\",")
        append("\"file\":\"").append(jsonEscape(file)).append("\",")
        append("\"sourcePath\":\"").append(jsonEscape(sourcePath)).append("\",")
        append("\"startLine\":").append(startLine).append(',')
        append("\"startColumn\":").append(startColumn).append(',')
        append("\"endLine\":").append(endLine).append(',')
        append("\"endColumn\":").append(endColumn).append(',')
        append("\"startOffset\":").append(startOffset).append(',')
        append("\"endOffset\":").append(endOffset).append(',')
        append("\"nameLine\":").append(nameLine)
        append('}')
    }

    companion object {
        fun listToJson(items: List<DeclarationInfo>): String =
            items.joinToString(",", "[", "]") { it.toJson() }

        fun listFromJson(json: String): List<DeclarationInfo> =
            parseJsonObjectArray(json).map { obj ->
                DeclarationInfo(
                    kind = DeclarationKind.valueOf((obj["kind"] as String).uppercase()),
                    fqn = obj["fqn"] as String,
                    name = obj["name"] as String,
                    signature = obj["signature"] as String,
                    file = obj["file"] as String,
                    sourcePath = obj["sourcePath"] as String,
                    startLine = (obj["startLine"] as String).toInt(),
                    startColumn = (obj["startColumn"] as String).toInt(),
                    endLine = (obj["endLine"] as String).toInt(),
                    endColumn = (obj["endColumn"] as String).toInt(),
                    startOffset = (obj["startOffset"] as String).toInt(),
                    endOffset = (obj["endOffset"] as String).toInt(),
                    nameLine = (obj["nameLine"] as String).toInt(),
                )
            }
    }
}
