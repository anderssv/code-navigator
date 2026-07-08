package no.f12.codenavigator.navigation.annotation

enum class AnnotationTarget {
    CLASS,
    METHOD,
    FIELD,
    ;

    companion object {
        val ALL: Set<AnnotationTarget> = entries.toSet()

        /** Parses a comma-separated list (case-insensitive). Unknown/blank values are ignored; an empty or all-unrecognized result falls back to [ALL]. */
        fun parse(values: List<String>): Set<AnnotationTarget> {
            val parsed = values
                .mapNotNull { raw -> entries.firstOrNull { it.name.equals(raw.trim(), ignoreCase = true) } }
                .toSet()
            return parsed.ifEmpty { ALL }
        }
    }
}
