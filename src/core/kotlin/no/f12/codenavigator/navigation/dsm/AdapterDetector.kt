package no.f12.codenavigator.navigation.dsm

import no.f12.codenavigator.navigation.types.ClassName

object AdapterDetector {

    fun detect(
        projectClasses: Set<ClassName>,
        projectDeps: List<PackageDependency>,
        externalDeps: List<PackageDependency>,
        signatureTypes: Map<ClassName, Set<ClassName>> = emptyMap(),
        compositionRoots: Set<ClassName> = emptySet(),
    ): Map<ClassName, AdapterReason> =
        projectClasses
            .mapNotNull { cls ->
                reasonFor(cls, projectClasses, projectDeps, externalDeps, signatureTypes, compositionRoots)
                    ?.let { cls to it }
            }
            .toMap()

    /**
     * Framework-only pass, used before composition roots are known. The topological rules need the
     * roots, and root detection needs to know what an adapter is — this breaks that cycle by answering
     * the half of the question that depends on nothing but the class itself.
     */
    fun detectFrameworkAdapters(
        projectClasses: Set<ClassName>,
        externalDeps: List<PackageDependency>,
        signatureTypes: Map<ClassName, Set<ClassName>> = emptyMap(),
    ): Map<ClassName, AdapterReason> =
        projectClasses
            .mapNotNull { cls -> frameworkReasonFor(cls, externalDeps, signatureTypes)?.let { cls to it } }
            .toMap()

    private fun frameworkReasonFor(
        cls: ClassName,
        externalDeps: List<PackageDependency>,
        signatureTypes: Map<ClassName, Set<ClassName>>,
    ): AdapterReason? {
        if (signatureTypes[cls].orEmpty().any { isFrameworkType(it) }) return AdapterReason.FRAMEWORK_SIGNATURE
        if (externalDeps.any { it.sourceClass == cls && isFrameworkType(it.targetClass) }) return AdapterReason.FRAMEWORK_TYPE
        return null
    }

    private fun reasonFor(
        cls: ClassName,
        projectClasses: Set<ClassName>,
        projectDeps: List<PackageDependency>,
        externalDeps: List<PackageDependency>,
        signatureTypes: Map<ClassName, Set<ClassName>>,
        compositionRoots: Set<ClassName>,
    ): AdapterReason? {
        frameworkReasonFor(cls, externalDeps, signatureTypes)?.let { return it }

        // Every class references String and Intrinsics; counting those as "talks to a library" would
        // make every leaf an adapter. The topological signals only mean anything for real libraries.
        val external = externalDeps.filter { it.sourceClass == cls }.map { it.targetClass }
        if (external.none { isLibraryType(it) }) return null

        val callers = projectDeps
            .filter { it.targetClass == cls && it.sourceClass != cls }
            .map { it.sourceClass }
            .toSet()

        // Nothing in the application calls it, but the composition root wires it: the framework drives
        // it. Without that wiring it is simply unreferenced — dead code or a test fixture, not an
        // adapter — which is why "no callers at all" deliberately falls through to null.
        if (callers.isNotEmpty() && callers.all { it in compositionRoots }) {
            return AdapterReason.UNCALLED_ENTRY_POINT
        }
        if (callers.isEmpty()) return null

        val outgoing = projectDeps.filter { it.sourceClass == cls && it.targetClass != cls && it.targetClass in projectClasses }
        if (outgoing.isEmpty()) return AdapterReason.SINK_WITH_EXTERNAL_CALLS

        return null
    }

    private fun isLibraryType(type: ClassName): Boolean =
        STDLIB_PACKAGES.none { prefix -> type.value.startsWith(prefix) }

    private fun isFrameworkType(type: ClassName): Boolean =
        FRAMEWORK_PACKAGES.any { prefix -> type.value.startsWith(prefix) }

    private val STDLIB_PACKAGES = setOf(
        "java.lang.", "java.util.", "java.math.", "java.time.", "java.text.",
        "kotlin.", "kotlinx.coroutines.",
        "org.jetbrains.annotations.",
    )

    private val FRAMEWORK_PACKAGES = setOf(
        "io.ktor", "org.springframework", "jakarta.", "javax.",
        "org.jetbrains.exposed", "org.hibernate",
        "io.quarkus", "io.vertx",
        "org.apache.http", "okhttp3", "java.net.http",
        "java.sql", "javax.sql",
        "com.zaxxer.hikari",
        "org.eclipse.microprofile",
        "io.grpc", "net.devh.boot.grpc",
    )
}
