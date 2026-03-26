package no.f12.codenavigator.navigation

import org.objectweb.asm.Opcodes

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
    private val VALUE_CLASS_SUFFIX = Regex("""^(box|unbox|equals|hashCode|toString|constructor)-impl\d*$""")
    private val MANGLED_COPY = Regex("""^copy-[A-Za-z0-9]+(\${'$'}default)?$""")
    private val DEFAULT_BRIDGE_SUFFIX = "\$default"
    private val FOR_INLINE_SUFFIX = "\$\$forInline"

    private val KOTLIN_ACCESSOR = Regex("""^(get|set|is)[A-Z]""")

    val EXCLUDED_FIELDS = setOf("INSTANCE")

    fun isGenerated(methodName: String): Boolean {
        if (methodName in EXCLUDED_METHODS) return true
        if (methodName.startsWith(SYNTHETIC_PREFIX)) return true
        if (DATA_CLASS_COMPONENT.matches(methodName)) return true
        if (methodName.startsWith("copy$")) return true
        if (MANGLED_COPY.matches(methodName)) return true
        if (LAMBDA_METHOD.containsMatchIn(methodName)) return true
        if (VALUE_CLASS_SUFFIX.matches(methodName)) return true
        if (methodName.endsWith(DEFAULT_BRIDGE_SUFFIX)) return true
        if (methodName.endsWith(FOR_INLINE_SUFFIX)) return true
        return false
    }

    fun isExcludedMethod(name: String, access: Int): Boolean {
        if (isGenerated(name)) return true
        if (access and Opcodes.ACC_SYNTHETIC != 0) return true
        return false
    }

    fun isAccessorForField(methodName: String, fieldNames: Set<String>): Boolean {
        if (!KOTLIN_ACCESSOR.containsMatchIn(methodName)) return false

        val prefix = when {
            methodName.startsWith("get") -> "get"
            methodName.startsWith("set") -> "set"
            methodName.startsWith("is") -> "is"
            else -> return false
        }
        val propertyName = methodName.removePrefix(prefix).replaceFirstChar { it.lowercase() }
        return propertyName in fieldNames
    }
}
