package no.f12.codenavigator.navigation.core

enum class SourceSet(val label: String) {
    MAIN("prod"),
    TEST("test"),
}

enum class Scope {
    ALL,
    PROD,
    TEST,
    ;

    fun matchesSourceSet(sourceSet: SourceSet): Boolean = when (this) {
        ALL -> true
        PROD -> sourceSet == SourceSet.MAIN
        TEST -> sourceSet == SourceSet.TEST
    }

    companion object {
        fun parse(value: String?): Scope = when (value?.lowercase()) {
            "prod" -> PROD
            "test" -> TEST
            else -> ALL
        }
    }
}

@JvmInline
value class ClassName(val value: String) : Comparable<ClassName> {
    fun packageName(): PackageName =
        PackageName(value.substringBeforeLast('.', ""))

    fun simpleName(): String =
        value.substringAfterLast('.').substringBefore('$')

    fun isTest(): Boolean {
        val simple = simpleName()
        return simple.endsWith("Test") || simple.endsWith("TestKt")
    }

    fun candidateNames(): List<String> {
        val candidates = mutableListOf<String>()
        fun strip(name: String) {
            for (suffix in STRIPPABLE_SUFFIXES) {
                if (name.endsWith(suffix)) {
                    val stripped = name.removeSuffix(suffix)
                    if (stripped.isNotEmpty()) {
                        candidates.add(stripped)
                        strip(stripped)
                    }
                }
            }
        }
        strip(simpleName())
        return candidates
    }

    fun isGenerated(): Boolean = '$' in value

    fun isPackageInfo(): Boolean = value.endsWith(".package-info")

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

    fun stripPackagePrefix(prefix: PackageName): ClassName =
        if (prefix.isNotEmpty() && value.startsWith("${prefix.value}.")) {
            ClassName(value.removePrefix("${prefix.value}."))
        } else {
            this
        }

    override fun compareTo(other: ClassName): Int = value.compareTo(other.value)

    override fun toString(): String = value

    companion object {
        private val STRIPPABLE_SUFFIXES = listOf("Kt", "Test")
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
value class AnnotationName(val value: String) : Comparable<AnnotationName> {
    fun simpleName(): String =
        value.substringAfterLast('.')

    fun packageName(): String =
        value.substringBeforeLast('.', "")

    fun matches(regex: Regex): Boolean = regex.containsMatchIn(value)

    fun isInternal(): Boolean = value in INTERNAL_ANNOTATIONS

    override fun compareTo(other: AnnotationName): Int = value.compareTo(other.value)

    override fun toString(): String = value

    companion object {
        private val INTERNAL_ANNOTATIONS = setOf(
            "kotlin.Metadata",
            "kotlin.jvm.internal.SourceDebugExtension",
            "kotlin.coroutines.jvm.internal.DebugMetadata",
            "org.jetbrains.annotations.NotNull",
            "org.jetbrains.annotations.Nullable",
        )
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
