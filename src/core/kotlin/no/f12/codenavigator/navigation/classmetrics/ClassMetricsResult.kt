package no.f12.codenavigator.navigation.classmetrics

import no.f12.codenavigator.navigation.types.ClassName
import no.f12.codenavigator.navigation.types.PackageName

enum class ClassCohesionVerdict {
    HIGH,
    MEDIUM,
    LOW,
    MONOLITH,
}

data class ClassMetricsResult(
    val className: ClassName,
    val packageName: PackageName,
    val totalMethods: Int,
    val tcc: Double,
    val lcc: Double,
    val verdict: ClassCohesionVerdict,
    val wmc: Int,
    val cbo: Int,
    val dit: Int,
)
