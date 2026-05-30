package no.f12.codenavigator.navigation.dsm

import no.f12.codenavigator.navigation.types.ClassName
import no.f12.codenavigator.navigation.types.PackageName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StructureGrouperTest {

    @Test
    fun `groups move suggestions by target package`() {
        val suggestions = MoveSuggestionResult(listOf(
            moveSuggestion("com.app.web.UserMapper", "com.app.web", "com.app.domain"),
            moveSuggestion("com.app.web.OrderMapper", "com.app.web", "com.app.domain"),
            moveSuggestion("com.app.infra.EmailFormatter", "com.app.infra", "com.app.domain"),
        ))

        val result = StructureGrouper.group(suggestions, totalClassCount = 20, minGroupSize = 1)

        assertEquals(1, result.groups.size)
        assertEquals(PackageName("com.app.domain"), result.groups[0].targetPackage)
        assertEquals(3, result.groups[0].classes.size)
    }

    @Test
    fun `filters out groups smaller than min-group-size`() {
        val suggestions = MoveSuggestionResult(listOf(
            moveSuggestion("com.app.web.UserMapper", "com.app.web", "com.app.domain"),
            moveSuggestion("com.app.web.OrderMapper", "com.app.web", "com.app.domain"),
            moveSuggestion("com.app.infra.EmailFormatter", "com.app.infra", "com.app.util"),
        ))

        val result = StructureGrouper.group(suggestions, totalClassCount = 20, minGroupSize = 2)

        assertEquals(1, result.groups.size)
        assertEquals(PackageName("com.app.domain"), result.groups[0].targetPackage)
    }

    @Test
    fun `returns empty groups when no suggestions`() {
        val result = StructureGrouper.group(MoveSuggestionResult(emptyList()), totalClassCount = 10, minGroupSize = 2)

        assertTrue(result.groups.isEmpty())
        assertEquals(0.0, result.driftScore)
    }

    @Test
    fun `computes drift score as percentage of misplaced classes`() {
        val suggestions = MoveSuggestionResult(listOf(
            moveSuggestion("com.app.web.UserMapper", "com.app.web", "com.app.domain"),
            moveSuggestion("com.app.web.OrderMapper", "com.app.web", "com.app.domain"),
        ))

        val result = StructureGrouper.group(suggestions, totalClassCount = 10, minGroupSize = 1)

        assertEquals(0.2, result.driftScore)
        assertEquals(2, result.misplacedCount)
        assertEquals(10, result.totalClassCount)
    }

    @Test
    fun `sorts groups by size descending`() {
        val suggestions = MoveSuggestionResult(listOf(
            moveSuggestion("com.app.a.A1", "com.app.a", "com.app.big"),
            moveSuggestion("com.app.a.A2", "com.app.a", "com.app.big"),
            moveSuggestion("com.app.a.A3", "com.app.a", "com.app.big"),
            moveSuggestion("com.app.b.B1", "com.app.b", "com.app.small"),
            moveSuggestion("com.app.b.B2", "com.app.b", "com.app.small"),
        ))

        val result = StructureGrouper.group(suggestions, totalClassCount = 20, minGroupSize = 1)

        assertEquals(2, result.groups.size)
        assertEquals(PackageName("com.app.big"), result.groups[0].targetPackage)
        assertEquals(PackageName("com.app.small"), result.groups[1].targetPackage)
    }

    private fun moveSuggestion(className: String, currentPkg: String, suggestedPkg: String) =
        MoveSuggestion(
            className = ClassName(className),
            currentPackage = PackageName(currentPkg),
            suggestedPackage = PackageName(suggestedPkg),
            edgesToCurrent = 1,
            edgesToSuggested = 3,
            confidence = 0.75,
        )
}
