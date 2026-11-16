package no.f12.codenavigator.registry

import java.io.File

sealed interface StalenessResult {
    data object Fresh : StalenessResult
    data class Stale(val warning: String) : StalenessResult
    data class NoClassFiles(val error: String) : StalenessResult
}

object ClassFileStaleness {

    private val SOURCE_EXTENSIONS = setOf("kt", "java")

    /** Directory names that hold build output / generated sources — pruned from the SOURCE scan so a
     * regenerated .kt/.java under them never counts as "newest source". Not pruned from the class scan,
     * since compiled classes legitimately live under build/target. */
    private val BUILD_DIR_NAMES = setOf("build", "target", "out", "generated", "generated-sources", ".gradle")

    fun check(sourceDirectories: List<File>, classDirectories: List<File>): StalenessResult {
        val newestClass = newestTimestamp(classDirectories, setOf("class"), pruneBuildDirs = false)
            ?: return StalenessResult.NoClassFiles(
                "NO COMPILED CLASSES FOUND. You must compile the project before running this command " +
                    "(e.g. './gradlew build' or 'mvn compile'). Results would otherwise be empty or misleading."
            )

        val newestSource = newestTimestamp(sourceDirectories, SOURCE_EXTENSIONS, pruneBuildDirs = true)
            ?: return StalenessResult.Fresh

        if (newestSource > newestClass) {
            return StalenessResult.Stale(
                "STALE BUILD: class files are OLDER than source files — results may not reflect your latest changes. " +
                    "Newest source file is ${formatTimestamp(newestSource)}, newest class file is ${formatTimestamp(newestClass)}. " +
                    "Changes after ${formatTime(newestClass)} are not reflected. Please recompile before trusting these results."
            )
        }

        return StalenessResult.Fresh
    }

    private fun newestTimestamp(directories: List<File>, extensions: Set<String>, pruneBuildDirs: Boolean): Long? {
        var newest: Long? = null
        for (dir in directories) {
            if (!dir.exists()) continue
            dir.walkTopDown()
                .onEnter { entered -> !(pruneBuildDirs && entered != dir && entered.name in BUILD_DIR_NAMES) }
                .filter { it.isFile && it.extension in extensions }
                .forEach { file ->
                    val mtime = file.lastModified()
                    if (newest == null || mtime > newest!!) newest = mtime
                }
        }
        return newest
    }

    private fun formatTimestamp(millis: Long): String {
        val instant = java.time.Instant.ofEpochMilli(millis)
        val local = java.time.LocalDateTime.ofInstant(instant, java.time.ZoneId.systemDefault())
        return java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").format(local)
    }

    private fun formatTime(millis: Long): String {
        val instant = java.time.Instant.ofEpochMilli(millis)
        val local = java.time.LocalDateTime.ofInstant(instant, java.time.ZoneId.systemDefault())
        return java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss").format(local)
    }
}

sealed interface StalenessDecision {
    data object Proceed : StalenessDecision
    data class Warn(val message: String) : StalenessDecision
    data class Fail(val message: String) : StalenessDecision
}

/**
 * Stale classes used to be a warning, and the task still answered. The answer could be confidently wrong (callers
 * missing, the wrong file), and an agent reading the result easily misses a warning, so stale now fails unless
 * explicitly allowed. When the build compiled first, the compiler's up-to-date check is authoritative: an mtime
 * comparison then only produces false alarms (a checkout touching sources the compiler rightly skipped).
 */
object StalenessPolicy {
    fun decide(result: StalenessResult, compiledFirst: Boolean, allowStale: Boolean): StalenessDecision = when (result) {
        is StalenessResult.Fresh -> StalenessDecision.Proceed
        is StalenessResult.NoClassFiles -> StalenessDecision.Fail(result.error)
        is StalenessResult.Stale -> when {
            compiledFirst -> StalenessDecision.Proceed
            allowStale -> StalenessDecision.Warn(result.warning)
            else -> StalenessDecision.Fail(
                "${result.warning}\nRefusing to answer from stale classes. Compile first (drop -Pcnav.compile=false), or pass " +
                    "-Pcnav.allowStale=true to analyse the last successful build anyway, e.g. while the code doesn't compile.",
            )
        }
    }
}
