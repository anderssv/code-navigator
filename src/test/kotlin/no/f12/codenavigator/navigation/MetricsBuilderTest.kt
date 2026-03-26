package no.f12.codenavigator.navigation

import no.f12.codenavigator.analysis.Hotspot
import kotlin.test.Test
import kotlin.test.assertEquals

class MetricsBuilderTest {

    @Test
    fun `counts total classes from class info list`() {
        val classes = listOf(
            ClassInfo(ClassName("com.example.Foo"), "Foo.kt", "com/example/Foo.kt", true),
            ClassInfo(ClassName("com.example.Bar"), "Bar.kt", "com/example/Bar.kt", true),
            ClassInfo(ClassName("com.example.Baz"), "Baz.kt", "com/example/Baz.kt", true),
        )

        val result = MetricsBuilder.build(
            classes = classes,
            packages = emptyList(),
            rankedTypes = emptyList(),
            cyclicPairCount = 0,
            deadCode = emptyList(),
            hotspots = emptyList(),
        )

        assertEquals(3, result.totalClasses)
    }

    @Test
    fun `counts packages from package list`() {
        val packages = listOf(PackageName("com.example.api"), PackageName("com.example.domain"), PackageName("com.example.infra"))

        val result = MetricsBuilder.build(
            classes = emptyList(),
            packages = packages,
            rankedTypes = emptyList(),
            cyclicPairCount = 0,
            deadCode = emptyList(),
            hotspots = emptyList(),
        )

        assertEquals(3, result.packageCount)
    }

    @Test
    fun `computes average fan-in and fan-out from ranked types`() {
        val rankedTypes = listOf(
            RankedType("com.example.Foo", 0.5, inDegree = 10, outDegree = 4),
            RankedType("com.example.Bar", 0.3, inDegree = 6, outDegree = 2),
        )

        val result = MetricsBuilder.build(
            classes = emptyList(),
            packages = emptyList(),
            rankedTypes = rankedTypes,
            cyclicPairCount = 0,
            deadCode = emptyList(),
            hotspots = emptyList(),
        )

        assertEquals(8.0, result.averageFanIn)
        assertEquals(3.0, result.averageFanOut)
    }

    @Test
    fun `counts cycles from cyclic pair count`() {
        val result = MetricsBuilder.build(
            classes = emptyList(),
            packages = emptyList(),
            rankedTypes = emptyList(),
            cyclicPairCount = 5,
            deadCode = emptyList(),
            hotspots = emptyList(),
        )

        assertEquals(5, result.cycleCount)
    }

    @Test
    fun `counts dead classes and dead methods separately`() {
        val deadCode = listOf(
            DeadCode(ClassName("com.example.Unused"), null, DeadCodeKind.CLASS, "Unused.kt"),
            DeadCode(ClassName("com.example.Foo"), "bar", DeadCodeKind.METHOD, "Foo.kt"),
            DeadCode(ClassName("com.example.Foo"), "baz", DeadCodeKind.METHOD, "Foo.kt"),
            DeadCode(ClassName("com.example.OldStuff"), null, DeadCodeKind.CLASS, "OldStuff.kt"),
        )

        val result = MetricsBuilder.build(
            classes = emptyList(),
            packages = emptyList(),
            rankedTypes = emptyList(),
            cyclicPairCount = 0,
            deadCode = deadCode,
            hotspots = emptyList(),
        )

        assertEquals(2, result.deadClassCount)
        assertEquals(2, result.deadMethodCount)
    }

    @Test
    fun `includes top hotspots`() {
        val hotspots = listOf(
            Hotspot("src/main/Foo.kt", 15, 200),
            Hotspot("src/main/Bar.kt", 10, 100),
        )

        val result = MetricsBuilder.build(
            classes = emptyList(),
            packages = emptyList(),
            rankedTypes = emptyList(),
            cyclicPairCount = 0,
            deadCode = emptyList(),
            hotspots = hotspots,
        )

        assertEquals(2, result.topHotspots.size)
        assertEquals("src/main/Foo.kt", result.topHotspots[0].file)
    }

    @Test
    fun `returns zero hotspots when hotspot list is empty`() {
        val result = MetricsBuilder.build(
            classes = emptyList(),
            packages = emptyList(),
            rankedTypes = emptyList(),
            cyclicPairCount = 0,
            deadCode = emptyList(),
            hotspots = emptyList(),
        )

        assertEquals(0, result.topHotspots.size)
    }

    @Test
    fun `averages are zero when ranked types list is empty`() {
        val result = MetricsBuilder.build(
            classes = emptyList(),
            packages = emptyList(),
            rankedTypes = emptyList(),
            cyclicPairCount = 0,
            deadCode = emptyList(),
            hotspots = emptyList(),
        )

        assertEquals(0.0, result.averageFanIn)
        assertEquals(0.0, result.averageFanOut)
    }
}
