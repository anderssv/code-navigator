package no.f12.codenavigator.navigation

object KotlinMethodFilter {

    private val EXCLUDED_METHODS = setOf(
        "<init>", "<clinit>",
        "toString", "hashCode", "equals",
        "copy",
        "\$values", "valueOf", "values",
        "main",
    )

    private val DATA_CLASS_COMPONENT = Regex("""^component\d+$""")
    private val LAMBDA_METHOD = Regex("""\${'$'}lambda\${'$'}""")
    private val SYNTHETIC_PREFIX = "access$"
    private val VALUE_CLASS_SUFFIX = Regex("""^(box|unbox|equals|hashCode|toString)-impl\d*$""")

    fun isGenerated(methodName: String): Boolean {
        if (methodName in EXCLUDED_METHODS) return true
        if (methodName.startsWith(SYNTHETIC_PREFIX)) return true
        if (DATA_CLASS_COMPONENT.matches(methodName)) return true
        if (methodName.startsWith("copy$")) return true
        if (LAMBDA_METHOD.containsMatchIn(methodName)) return true
        if (VALUE_CLASS_SUFFIX.matches(methodName)) return true
        return false
    }
}
