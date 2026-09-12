package no.f12.codenavigator.navigation.testcoupling

import no.f12.codenavigator.navigation.types.ClassName

/**
 * Distinguishes a mutating (write) port method from a read one. Read-side pass-through from a
 * driving adapter straight to a port (`fun get(id) = repository.findById(id)`) is idiomatic and
 * harmless — flagging it would bury the real signal, which is use-case orchestration hiding behind
 * a *write*. Only writes are checked for [CouplingSubjectKind.ADAPTER]; [CouplingSubjectKind.TEST]
 * is unaffected (TTTD cares about any port coupling in a test, read or write).
 */
object PortMethodClassifier {

    // Ordered by how reliably the prefix signals a mutation. Matched as a prefix (not a whole-word
    // match) so "updatePoll", "addApplication", "finalizePoll" all match, without also matching
    // unrelated method names that merely contain one of these words mid-string.
    private val WRITE_PREFIXES = listOf(
        "add", "update", "delete", "save", "create", "insert", "remove",
        "finalize", "migrate", "persist", "publish", "send", "register",
        "revoke", "archive", "schedule", "cancel", "set",
    )

    fun isWrite(
        portInterface: ClassName,
        methodName: String,
        writeOverrides: Set<String>,
        readOverrides: Set<String>,
    ): Boolean {
        val simpleName = portInterface.value.substringAfterLast('.')
        val qualified = "$simpleName.$methodName"
        if (qualified in writeOverrides) return true
        if (qualified in readOverrides) return false
        return WRITE_PREFIXES.any { prefix -> methodName.startsWith(prefix, ignoreCase = true) }
    }
}
