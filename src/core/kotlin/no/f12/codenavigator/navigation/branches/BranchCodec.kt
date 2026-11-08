package no.f12.codenavigator.navigation.branches

/**
 * Line-based text encoding of [FunctionBranches], used to carry scan results out of the Gradle PSI worker
 * (which runs in its own classloader). One record per line, tab-separated, items prefixed by nesting depth.
 */
object BranchCodec {

    private const val FUNCTION = "F"
    private const val BRANCH = "B"
    private const val CALL = "C"
    private const val EXIT = "E"

    fun encode(functions: List<FunctionBranches>): String = buildString {
        for (fn in functions) {
            appendLine(record(FUNCTION, fn.className, fn.name, fn.startLine, fn.endLine))
            encodeItems(fn.body, 0)
        }
    }

    private fun StringBuilder.encodeItems(items: List<BranchItem>, depth: Int) {
        for (item in items) {
            when (item) {
                is CallSite -> appendLine(record(CALL, depth, item.line, item.name, if (item.property) "P" else "-"))
                is Exit -> appendLine(record(EXIT, depth, item.line, item.kind.name, item.text))
                is Branch -> {
                    appendLine(record(BRANCH, depth, item.line, item.kind.name, item.label))
                    encodeItems(item.children, depth + 1)
                }
            }
        }
    }

    fun decode(text: String): List<FunctionBranches> {
        val functions = mutableListOf<FunctionBranches>()
        var header: List<String>? = null
        // stack[d] collects the items at depth d; a branch at depth d owns stack[d + 1].
        val stack = mutableListOf<MutableList<BranchItem>>()
        val pending = mutableListOf<List<String>>()

        fun closeTo(depth: Int) {
            while (stack.size > depth + 1) {
                val children = stack.removeLast()
                val fields = pending.removeLast()
                stack.last() += Branch(BranchKind.valueOf(fields[3]), fields[4], fields[2].toInt(), children)
            }
        }

        fun finishFunction() {
            val h = header ?: return
            closeTo(0)
            functions += FunctionBranches(h[1], h[2], h[3].toInt(), h[4].toInt(), stack.firstOrNull().orEmpty())
        }

        for (line in text.lineSequence().filter { it.isNotEmpty() }) {
            val fields = line.split('\t').map(::unescape)
            if (fields[0] == FUNCTION) {
                finishFunction()
                header = fields
                stack.clear()
                pending.clear()
                stack += mutableListOf<BranchItem>()
                continue
            }
            val depth = fields[1].toInt()
            closeTo(depth)
            when (fields[0]) {
                CALL -> stack.last() += CallSite(fields[3], fields[2].toInt(), property = fields[4] == "P")
                EXIT -> stack.last() += Exit(ExitKind.valueOf(fields[3]), fields[4], fields[2].toInt())
                BRANCH -> {
                    pending += fields
                    stack += mutableListOf<BranchItem>()
                }
            }
        }
        finishFunction()
        return functions
    }

    private fun record(vararg fields: Any): String = fields.joinToString("\t") { escape(it.toString()) }

    private fun escape(s: String): String = s.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n")

    private fun unescape(s: String): String = buildString {
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                when (s[i + 1]) {
                    't' -> append('\t')
                    'n' -> append('\n')
                    else -> append(s[i + 1])
                }
                i += 2
            } else {
                append(c)
                i++
            }
        }
    }
}
