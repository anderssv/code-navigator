package no.f12.codenavigator.navigation.declaration

/**
 * Pattern semantics match the other `find-*` tasks: case-insensitive regex, camelCase-enhanced by
 * `TaskRegistry` before it gets here.
 *
 * - Unqualified pattern (no `.`): matched against the declaration's own name (`place`, `OrderService`).
 * - Qualified pattern (contains `.`): matched anywhere in the dotted FQN (`OrderService.place`,
 *   `com.example.*`) or in the source path (`variants/declarations`).
 */
object DeclarationFilter {
    fun filter(declarations: List<DeclarationInfo>, pattern: String, kind: DeclarationKindGroup?): List<DeclarationInfo> {
        val regex = Regex(pattern, RegexOption.IGNORE_CASE)
        val isQualified = '.' in pattern
        return declarations.filter { d ->
            (kind == null || d.kind.group == kind) &&
                if (isQualified) {
                    regex.containsMatchIn(d.fqn) || regex.containsMatchIn(d.sourcePath)
                } else {
                    regex.containsMatchIn(d.name)
                }
        }
    }
}
