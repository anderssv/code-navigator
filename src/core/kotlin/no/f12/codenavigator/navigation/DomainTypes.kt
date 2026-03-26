package no.f12.codenavigator.navigation

@JvmInline
value class ClassName(val value: String) : Comparable<ClassName> {
    fun packageName(): PackageName =
        PackageName(value.substringBeforeLast('.', ""))

    fun simpleName(): String =
        value.substringAfterLast('.').substringBefore('$')

    fun isGenerated(): Boolean = '$' in value

    fun isSynthetic(): Boolean =
        SYNTHETIC_SUFFIX.containsMatchIn(value) ||
            LAMBDA_PATTERN.containsMatchIn(value)

    fun outerClass(): ClassName {
        val idx = value.lastIndexOf('$')
        return if (idx < 0) this else ClassName(value.substring(0, idx))
    }

    fun matches(regex: Regex): Boolean = regex.containsMatchIn(value)

    fun startsWith(prefix: String): Boolean = value.startsWith(prefix)

    override fun compareTo(other: ClassName): Int = value.compareTo(other.value)

    override fun toString(): String = value

    companion object {
        private val SYNTHETIC_SUFFIX = Regex("""\$\d+$""")
        private val LAMBDA_PATTERN = Regex("""\${'$'}lambda\${'$'}""")

        fun fromInternal(internalName: String): ClassName =
            ClassName(internalName.replace('/', '.'))
    }
}

@JvmInline
value class PackageName(val value: String) : Comparable<PackageName> {
    fun isEmpty(): Boolean = value.isEmpty()

    fun isNotEmpty(): Boolean = value.isNotEmpty()

    fun matches(regex: Regex): Boolean = regex.containsMatchIn(value)

    fun startsWith(prefix: String): Boolean = value.startsWith(prefix)

    fun startsWith(prefix: PackageName): Boolean = value.startsWith(prefix.value)

    fun truncate(rootPrefix: PackageName, depth: Int): PackageName {
        val stripped = if (rootPrefix.isNotEmpty() && value.startsWith("${rootPrefix.value}.")) {
            value.removePrefix("${rootPrefix.value}.")
        } else {
            value
        }
        val segments = stripped.split(".")
        return PackageName(segments.take(depth).joinToString("."))
    }

    override fun compareTo(other: PackageName): Int = value.compareTo(other.value)

    override fun toString(): String = value
}
