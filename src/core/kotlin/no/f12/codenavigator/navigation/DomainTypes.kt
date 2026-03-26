package no.f12.codenavigator.navigation

@JvmInline
value class ClassName(val value: String) : Comparable<ClassName> {
    fun packageName(): PackageName =
        PackageName(value.substringBeforeLast('.', ""))

    fun simpleName(): String =
        value.substringAfterLast('.').substringBefore('$')

    fun isGenerated(): Boolean = '$' in value

    fun isSynthetic(): Boolean =
        TRAILING_NUMERIC_SEGMENT.containsMatchIn(value) ||
            LAMBDA_PATTERN.containsMatchIn(value)

    fun outerClass(): ClassName {
        val idx = value.lastIndexOf('$')
        return if (idx < 0) this else ClassName(value.substring(0, idx))
    }

    fun topLevelClass(): ClassName {
        val idx = value.indexOf('$')
        return if (idx < 0) this else ClassName(value.substring(0, idx))
    }

    fun collapseLambda(): ClassName {
        var result = value
        while (true) {
            val afterNumeric = result.replace(TRAILING_NUMERIC_SEGMENT, "")
            if (afterNumeric == result) break
            result = afterNumeric.replace(TRAILING_LOWERCASE_SEGMENT, "")
        }
        return ClassName(result)
    }

    fun displayName(): String = value.replace('$', '.')

    fun packagePath(): String =
        packageName().value.replace('.', '/')

    fun matches(regex: Regex): Boolean = regex.containsMatchIn(value)

    fun startsWith(prefix: PackageName): Boolean = value.startsWith(prefix.value)

    override fun compareTo(other: ClassName): Int = value.compareTo(other.value)

    override fun toString(): String = value

    companion object {
        private val TRAILING_NUMERIC_SEGMENT = Regex("""\$\d+$""")
        private val TRAILING_LOWERCASE_SEGMENT = Regex("""\$[a-z][^$]*$""")
        private val LAMBDA_PATTERN = Regex("""\${'$'}lambda\${'$'}""")
        private val UNANCHORED_NUMERIC_SEGMENT = Regex("""\$\d+""")

        fun fromInternal(internalName: String): ClassName =
            ClassName(internalName.replace('/', '.'))

        fun isSyntheticName(name: String): Boolean =
            UNANCHORED_NUMERIC_SEGMENT.containsMatchIn(name) ||
                LAMBDA_PATTERN.containsMatchIn(name)
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
