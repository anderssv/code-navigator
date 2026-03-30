package no.f12.codenavigator.gradle

open class CodeNavigatorExtension {
    var rootPackage: String = ""
    var packageFilter: String = ""
    var includeExternal: Boolean = false

    fun resolveProperties(cliProperties: Map<String, String?>): Map<String, String?> {
        val result = cliProperties.toMutableMap()

        if ("package-filter" !in result) {
            val extensionFilter = packageFilter.ifEmpty { rootPackage }
            if (extensionFilter.isNotEmpty()) {
                result["package-filter"] = extensionFilter
            }
        }

        if ("include-external" !in result && includeExternal) {
            result["include-external"] = "true"
        }

        if ("root-package" !in result && rootPackage.isNotEmpty()) {
            result["root-package"] = rootPackage
        }

        return result
    }
}
