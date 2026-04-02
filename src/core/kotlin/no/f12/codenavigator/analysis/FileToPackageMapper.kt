package no.f12.codenavigator.analysis

object FileToPackageMapper {

    private val SOURCE_ROOTS = listOf(
        "src/main/kotlin/",
        "src/main/java/",
        "src/test/kotlin/",
        "src/test/java/",
    )

    fun map(filePath: String): String? {
        val root = SOURCE_ROOTS.firstOrNull { filePath.startsWith(it) } ?: return null
        val relativePath = filePath.removePrefix(root)
        val lastSlash = relativePath.lastIndexOf('/')
        if (lastSlash < 0) return ""
        return relativePath.substring(0, lastSlash).replace('/', '.')
    }
}
