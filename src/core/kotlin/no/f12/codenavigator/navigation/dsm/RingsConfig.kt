package no.f12.codenavigator.navigation.dsm

import no.f12.codenavigator.navigation.refactor.parseJsonObject
import java.io.File

data class RingsConfig(
    val expectedRingCount: Int? = null,
    val compositionRoots: List<String> = emptyList(),
    val adapters: List<String> = emptyList(),
    val notAdapters: List<String> = emptyList(),
    val valuePackages: List<String> = emptyList(),
    val frameworkPackages: List<String> = emptyList(),
) {
    companion object {
        private const val CONFIG_FILE_NAME = "cnav-config.json"

        @Suppress("UNCHECKED_CAST")
        fun fromJson(json: String): RingsConfig {
            val root = parseJsonObject(json)
            require(root["ringNames"] == null) {
                "cnav-config.json 'ringNames' is no longer supported: cnavRings detects rings from " +
                    "dependency inversions, so the ring count is not declared. Use the 'rings' section " +
                    "with 'expected', 'compositionRoots', 'adapters' and 'notAdapters' instead."
            }
            require(root["hints"] == null) {
                "cnav-config.json 'hints' is no longer supported: it mapped classes onto a fixed ring " +
                    "ladder that no longer exists. Use the 'rings' section's 'adapters' / 'notAdapters' " +
                    "to correct adapter classification instead."
            }

            val rings = root["rings"] as? Map<String, Any?> ?: return RingsConfig()
            return RingsConfig(
                expectedRingCount = (rings["expected"] as? String)?.toIntOrNull()
                    ?: (rings["expected"] as? Number)?.toInt(),
                compositionRoots = stringList(rings["compositionRoots"]),
                adapters = stringList(rings["adapters"]),
                notAdapters = stringList(rings["notAdapters"]),
                valuePackages = stringList(rings["valuePackages"]),
                frameworkPackages = stringList(rings["frameworkPackages"]),
            )
        }

        private fun stringList(value: Any?): List<String> =
            (value as? List<*>)?.filterIsInstance<String>() ?: emptyList()

        fun matchesGlob(className: String, pattern: String): Boolean = when {
            pattern == "*" -> true
            !pattern.contains("*") -> className == pattern
            pattern.startsWith("*") && pattern.endsWith("*") -> className.contains(pattern.removeSurrounding("*"))
            pattern.startsWith("*") -> className.endsWith(pattern.removePrefix("*"))
            pattern.endsWith("*") -> className.startsWith(pattern.removeSuffix("*"))
            else -> false
        }

        fun loadFromDirectory(directory: File): RingsConfig {
            val file = File(directory, CONFIG_FILE_NAME)
            return if (file.isFile) fromJson(file.readText()) else RingsConfig()
        }
    }
}
