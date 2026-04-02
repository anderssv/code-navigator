package no.f12.codenavigator.navigation.core

object RootPackageDetector {
    fun detect(packageNames: List<PackageName>): PackageName {
        val nonEmpty = packageNames.filter { it.isNotEmpty() }
        if (nonEmpty.isEmpty()) return PackageName("")

        val segmentLists = nonEmpty.map { it.value.split(".") }
        val first = segmentLists.first()

        val commonSegments = first.indices.takeWhile { i ->
            segmentLists.all { it.size > i && it[i] == first[i] }
        }

        return PackageName(first.take(commonSegments.size).joinToString("."))
    }

    fun detectFromClassNames(classNames: List<ClassName>): PackageName {
        val packageNames = classNames.map { it.packageName() }.distinct()
        return detect(packageNames)
    }
}
