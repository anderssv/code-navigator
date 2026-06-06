package no.f12.codenavigator.navigation.dsm

import no.f12.codenavigator.navigation.refactor.parseJsonObject
import no.f12.codenavigator.navigation.types.ClassName

data class HintPattern(
    val ringName: String,
    val pattern: String,
)

data class RingsHintsConfig(
    val ringNames: List<String>?,
    val hints: Map<String, List<String>>,
    val overrides: Map<String, String>,
) {
    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromJson(json: String): RingsHintsConfig {
            val obj = parseJsonObject(json)
            val hints = (obj["hints"] as? Map<String, List<*>>)
                ?.mapValues { (_, v) -> v.filterIsInstance<String>() }
                ?: emptyMap()
            val overrides = (obj["overrides"] as? Map<String, String>)
                ?: emptyMap()
            val ringNames = obj["ringNames"]?.let {
                (it as? List<*>)?.filterIsInstance<String>()
            }
            return RingsHintsConfig(
                ringNames = ringNames,
                hints = hints,
                overrides = overrides,
            )
        }

        private val CONFIG_FILE_NAMES = listOf("cnav-config.json")

        fun loadFromDirectory(directory: java.io.File): RingsHintsConfig? {
            for (fileName in CONFIG_FILE_NAMES) {
                val file = java.io.File(directory, fileName)
                if (file.isFile) {
                    return fromJson(file.readText())
                }
            }
            return null
        }

        fun matchesGlob(className: String, pattern: String): Boolean {
            return when {
                pattern == "*" -> true
                !pattern.contains("*") -> className == pattern
                pattern.startsWith("*") && pattern.endsWith("*") ->
                    className.contains(pattern.removeSurrounding("*"))
                pattern.startsWith("*") ->
                    className.endsWith(pattern.removePrefix("*"))
                pattern.endsWith("*") ->
                    className.startsWith(pattern.removeSuffix("*"))
                else -> false
            }
        }
    }

    fun toHintList(): List<HintPattern> =
        hints.entries.flatMap { (ringName, patterns) ->
            patterns.map { HintPattern(ringName, it) }
        }

    fun findHint(hintList: List<HintPattern>, simpleName: String): String? {
        for (hint in hintList) {
            if (matchesGlob(simpleName, hint.pattern)) return hint.ringName
        }
        return null
    }

    fun defaultRingNames(): List<String> =
        ringNames ?: listOf("domain", "port", "application", "infrastructure", "web-output", "web-input", "composition-root")

    fun ringOrder(ringName: String): Int? {
        val names = defaultRingNames()
        val idx = names.indexOf(ringName)
        return if (idx >= 0) idx else null
    }

    private fun stripSuffixes(name: String): String =
        name.removeSuffix("Kt").removeSuffix("Test")

    fun applyHint(
        simpleName: String,
        rawRing: Int,
        overrides: Map<String, String> = this.overrides,
        fqcn: String? = null,
    ): Int {
        val overrideRing = fqcn?.let { overrides[it] }
        if (overrideRing != null) {
            val overrideOrder = ringOrder(overrideRing) ?: return rawRing
            return maxOf(rawRing, overrideOrder)
        }

        val hintList = toHintList()
        val stripped = stripSuffixes(simpleName)
        val matchedRing = findHint(hintList, stripped) ?: return rawRing
        val hintOrder = ringOrder(matchedRing) ?: return rawRing

        return maxOf(rawRing, hintOrder)
    }

    fun ringIndexNames(): Map<Int, String> =
        defaultRingNames().mapIndexed { idx, name -> idx to name }.toMap()

    fun hasHints(): Boolean = hints.isNotEmpty() || overrides.isNotEmpty()

    fun adjustRings(rawRings: Map<ClassName, Int>, projectClasses: Set<ClassName>): Map<ClassName, Int> {
        if (hints.isEmpty() && overrides.isEmpty()) return rawRings
        return rawRings.mapValues { (className, rawRing) ->
            val simpleName = className.simpleName()
            val fqcn = className.value
            applyHint(simpleName, rawRing, fqcn = fqcn)
        }
    }
}
