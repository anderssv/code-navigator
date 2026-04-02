package no.f12.codenavigator.navigation.dsm

import no.f12.codenavigator.navigation.core.PackageName

object PackageDistanceCalculator {

    fun distance(a: PackageName, b: PackageName): Int {
        if (a == b) return 0
        val segmentsA = if (a.isEmpty()) emptyList() else a.value.split(".")
        val segmentsB = if (b.isEmpty()) emptyList() else b.value.split(".")
        val commonLength = segmentsA.zip(segmentsB).takeWhile { (s1, s2) -> s1 == s2 }.size
        return (segmentsA.size - commonLength) + (segmentsB.size - commonLength)
    }
}
