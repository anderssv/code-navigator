package no.f12.codenavigator.config

import no.f12.codenavigator.navigation.refactor.parseJsonObject
import java.io.File

/**
 * Reads the `defaults` section of `cnav-config.json` — per-task CLI defaults applied before
 * explicit task properties, so a project can commit shared settings (format, scope, etc.)
 * instead of every CLI invocation repeating the same flags.
 *
 * Any key here works for any task that has a matching [no.f12.codenavigator.registry.ParamDef]
 * name — there is no per-task allowlist. Unknown keys are silently ignored by tasks that don't
 * have a matching param, exactly like an unrecognized CLI flag would be.
 *
 * `hints`/`overrides`/`ringNames` (the ring-classifier config) live in the same file but are
 * parsed separately by [no.f12.codenavigator.navigation.dsm.RingsHintsConfig] — kept independent
 * to avoid touching that already-tested parser.
 */
object CnavConfig {
    private const val CONFIG_FILE_NAME = "cnav-config.json"

    /** Reads the top-level `defaults` object as string values. Returns an empty map if the file or the section is absent. */
    fun loadDefaults(directory: File): Map<String, String> {
        val file = File(directory, CONFIG_FILE_NAME)
        if (!file.isFile) return emptyMap()

        val obj = parseJsonObject(file.readText())
        val defaults = obj["defaults"] as? Map<*, *> ?: return emptyMap()

        return defaults.entries.mapNotNull { (key, value) ->
            (key as? String)?.let { it to stringify(value) }
        }.toMap()
    }

    private fun stringify(value: Any?): String = when (value) {
        null -> ""
        is List<*> -> value.joinToString(",")
        else -> value.toString()
    }

    /**
     * Merges `cnav-config.json`'s `defaults` under [properties] — any key already present in
     * [properties] (i.e. explicitly set on the CLI) wins over the config-file default.
     */
    fun applyDefaults(properties: Map<String, String?>, directory: File): Map<String, String?> {
        val defaults = loadDefaults(directory)
        if (defaults.isEmpty()) return properties
        return defaults + properties
    }
}
