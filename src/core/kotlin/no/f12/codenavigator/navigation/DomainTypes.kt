package no.f12.codenavigator.navigation

@JvmInline
value class ClassName(val value: String) {
    fun packageName(): PackageName =
        PackageName(value.substringBeforeLast('.', ""))

    override fun toString(): String = value
}

@JvmInline
value class PackageName(val value: String) {
    override fun toString(): String = value
}
