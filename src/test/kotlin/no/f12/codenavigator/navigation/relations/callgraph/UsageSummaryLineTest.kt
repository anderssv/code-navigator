package no.f12.codenavigator.navigation.relations.callgraph

import no.f12.codenavigator.navigation.types.ClassName
import no.f12.codenavigator.navigation.types.SourceSet
import kotlin.test.Test
import kotlin.test.assertEquals

class UsageSummaryLineTest {

    private fun site(cls: String, file: String, set: SourceSet?) =
        UsageSite(ClassName(cls), "m", file, ClassName("a.Person"), "actionsOn", "()V", UsageKind.METHOD_CALL, set)

    @Test
    fun `summarises references, files and prod vs test, telling same-named files in different packages apart`() {
        val usages = listOf(
            site("a.web.Routes", "Routes.kt", SourceSet.MAIN),
            site("a.web.Routes", "Routes.kt", SourceSet.MAIN),
            site("a.admin.Routes", "Routes.kt", SourceSet.MAIN),
            site("a.web.RoutesTest", "RoutesTest.kt", SourceSet.TEST),
        )

        assertEquals("4 references in 3 files (prod 2, test 1)", UsageFormatter.summaryLine(usages))
    }
}
