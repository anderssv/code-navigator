package no.f12.codenavigator.navigation.branches

import no.f12.codenavigator.formatting.JsonRaw
import no.f12.codenavigator.formatting.jsonArray
import no.f12.codenavigator.formatting.jsonObject
import no.f12.codenavigator.formatting.jsonStringArray
import no.f12.codenavigator.navigation.relations.callgraph.MethodRef

/**
 * Prints placed branches. Calls are printed by the caller's [renderCall] (given the callee and the indent to
 * use), so a call tree can nest each callee's own subtree at the right spot.
 */
object BranchFormatter {

    const val UNPLACED_LABEL = "(branch not known)"
    private const val STEP = "  "

    fun renderText(body: PlacedBody, indent: String, renderCall: (MethodRef, String) -> String): String =
        render(body, indent, renderCall) { line -> "  :$line" }

    fun renderLlm(body: PlacedBody, indent: String, renderCall: (MethodRef, String) -> String): String =
        render(body, indent, renderCall) { line -> " L$line" }

    private fun render(body: PlacedBody, indent: String, renderCall: (MethodRef, String) -> String, lineRef: (Int) -> String): String {
        val lines = mutableListOf<String>()
        fun items(items: List<PlacedItem>, indent: String) {
            for (item in items) {
                when (item) {
                    is PlacedCall -> lines += renderCall(item.callee, indent)
                    is PlacedExit -> lines += "$indent${item.text}${lineRef(item.line)}"
                    is PlacedBranch -> {
                        lines += "$indent${item.label}${lineRef(item.line)}"
                        items(item.children, indent + STEP)
                    }
                }
            }
        }
        items(body.items, indent)
        if (body.unplaced.isNotEmpty()) {
            lines += "$indent$UNPLACED_LABEL"
            body.unplaced.forEach { lines += renderCall(it, indent + STEP) }
        }
        return lines.joinToString("\n")
    }

    fun renderJson(body: PlacedBody): String = jsonObject(
        "items" to JsonRaw(itemsJson(body.items)),
        "unplaced" to JsonRaw(jsonStringArray(body.unplaced.map { it.qualifiedName })),
    )

    private fun itemsJson(items: List<PlacedItem>): String = jsonArray(items) { item ->
        when (item) {
            is PlacedCall -> jsonObject("call" to item.callee.qualifiedName, "line" to item.line)
            is PlacedExit -> jsonObject("exit" to item.text, "kind" to item.kind.name, "line" to item.line)
            is PlacedBranch -> jsonObject("branch" to item.label, "kind" to item.kind.name, "line" to item.line, "items" to JsonRaw(itemsJson(item.children)))
        }
    }

    /** `if (a) › try` for one call; alternatives (several calls) joined with ` | `; `always` for an unconditional call. */
    fun conditionText(chains: List<List<String>>?): String? =
        chains?.joinToString(" | ") { chain -> if (chain.isEmpty()) "always" else chain.joinToString(" › ") }

    fun conditionsJson(chains: List<List<String>>?): String? =
        chains?.let { jsonArray(it) { chain -> jsonStringArray(chain) } }
}
