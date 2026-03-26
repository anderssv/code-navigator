package no.f12.codenavigator.navigation

@JvmInline
value class ClassName(val value: String) : Comparable<ClassName> {
    fun packageName(): PackageName =
        PackageName(value.substringBeforeLast('.', ""))

    fun isGenerated(): Boolean = '$' in value

    fun isSynthetic(): Boolean =
        SYNTHETIC_SUFFIX.containsMatchIn(value) ||
            LAMBDA_PATTERN.containsMatchIn(value)

    override fun compareTo(other: ClassName): Int = value.compareTo(other.value)

    override fun toString(): String = value

    companion object {
        private val SYNTHETIC_SUFFIX = Regex("""\$\d+$""")
        private val LAMBDA_PATTERN = Regex("""\${'$'}lambda\${'$'}""")
    }
}

@JvmInline
value class PackageName(val value: String) : Comparable<PackageName> {
    override fun compareTo(other: PackageName): Int = value.compareTo(other.value)

    override fun toString(): String = value
}
