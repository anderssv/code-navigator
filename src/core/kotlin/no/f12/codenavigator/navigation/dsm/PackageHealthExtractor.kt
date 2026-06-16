package no.f12.codenavigator.navigation.dsm

import no.f12.codenavigator.navigation.deadcode.ReceiverTypeExtractor
import no.f12.codenavigator.navigation.types.ClassName
import no.f12.codenavigator.navigation.types.PackageName
import no.f12.codenavigator.navigation.bytecode.SkippedFileReporter
import no.f12.codenavigator.navigation.bytecode.scanProjectClasses
import java.io.File

/**
 * Shared extraction for tasks that need the same DsmDependencyExtractor call with
 * includeSamePackage=true (cohesion, move-suggest).
 */
data class PackageHealthExtraction(
    val dependencies: List<PackageDependency>,
    val structuralSupertypes: List<StructuralSupertypeInfo>,
    /**
     * Receiver types of Kotlin extension functions, keyed by the Kt-facade class.
     * Used by MoveSuggester to boost gravity toward the receiver's package and to
     * suppress facades whose receiver is entirely external (Rule 2 adapter signal).
     */
    val receiverTypes: Map<ClassName, Set<ClassName>>,
    /** All project classes — used to distinguish internal vs external receiver types. */
    val projectClasses: Set<ClassName>,
    val skippedFileWarning: String?,
)

object PackageHealthExtractor {

    fun extract(
        classDirectories: List<File>,
        packageFilter: String?,
        reportFile: File,
    ): PackageHealthExtraction {
        val projectClasses = scanProjectClasses(classDirectories)
        val filter = packageFilter?.let { PackageName(it) }

        val extractResult = DsmDependencyExtractor.extract(
            classDirectories, projectClasses,
            packageFilter = filter,
            includeExternal = false,
            filterTargets = false,
            includeSamePackage = true,
        )
        val structuralSupertypes = if (filter != null) {
            DsmDependencyExtractor.extractStructuralSupertypes(classDirectories, projectClasses)
                .filter { it.sourceClass.startsWith(filter) }
        } else {
            DsmDependencyExtractor.extractStructuralSupertypes(classDirectories, projectClasses)
        }
        val receiverTypes = ReceiverTypeExtractor.scanAll(classDirectories)
        val skippedWarning = SkippedFileReporter.report(extractResult.skippedFiles, reportFile)

        return PackageHealthExtraction(
            dependencies = extractResult.data,
            structuralSupertypes = structuralSupertypes,
            receiverTypes = receiverTypes,
            projectClasses = projectClasses,
            skippedFileWarning = skippedWarning,
        )
    }
}
