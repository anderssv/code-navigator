package no.f12.codenavigator.navigation.converge

import no.f12.codenavigator.navigation.types.ClassName
import no.f12.codenavigator.navigation.types.PackageName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConvergeFormatterTest {

    private val actNowEdge = ConvergedEdge(
        source = PackageName("com.example.api"),
        target = PackageName("com.example.service"),
        verdict = ConvergeVerdict.ACT_NOW,
        hasCycle = true,
        hasRingViolation = true,
        couplingDegree = 75,
    )

    private val latentEdge = ConvergedEdge(
        source = PackageName("com.example.a"),
        target = PackageName("com.example.b"),
        verdict = ConvergeVerdict.LATENT,
        hasCycle = true,
        hasRingViolation = false,
        couplingDegree = null,
    )

    @Test
    fun `TEXT groups edges by verdict in ACT_NOW, LATENT, MISSING_ABSTRACTION order`() {
        val output = ConvergeOutput.Intersect(ConvergeIntersectOutput(listOf(latentEdge, actNowEdge), 0, null))

        val text = ConvergeFormatter.format(output)

        assertTrue(text.indexOf("ACT NOW:") < text.indexOf("LATENT:"))
        assertTrue(text.contains("com.example.api <-> com.example.service"))
    }

    @Test
    fun `TEXT reports empty intersect result`() {
        val output = ConvergeOutput.Intersect(ConvergeIntersectOutput(emptyList(), 0, null))

        assertEquals("No converging structural/coupling signals found.", ConvergeFormatter.format(output))
    }

    @Test
    fun `TEXT mentions unresolved coupling pairs when present`() {
        val output = ConvergeOutput.Intersect(ConvergeIntersectOutput(listOf(actNowEdge), 3, null))

        val text = ConvergeFormatter.format(output)

        assertTrue(text.contains("3 coupled file pair(s) could not be resolved"))
    }

    @Test
    fun `TEXT appends advisory when present`() {
        val output = ConvergeOutput.Intersect(ConvergeIntersectOutput(listOf(actNowEdge), 0, null, advisory = "40 findings — a large result set"))

        val text = ConvergeFormatter.format(output)

        assertTrue(text.contains("⚠ 40 findings — a large result set"))
    }

    @Test
    fun `TEXT has no advisory line when advisory is null`() {
        val output = ConvergeOutput.Intersect(ConvergeIntersectOutput(listOf(actNowEdge), 0, null))

        assertTrue(!ConvergeFormatter.format(output).contains("⚠"))
    }

    @Test
    fun `JSON includes advisory field when present`() {
        val output = ConvergeOutput.Intersect(ConvergeIntersectOutput(listOf(actNowEdge), 0, null, advisory = "narrow with --scope=prod"))

        assertTrue(ConvergeFormatter.formatJson(output).contains("\"advisory\":\"narrow with --scope=prod\""))
    }

    @Test
    fun `JSON omits advisory field when null`() {
        val output = ConvergeOutput.Intersect(ConvergeIntersectOutput(listOf(actNowEdge), 0, null))

        assertTrue(!ConvergeFormatter.formatJson(output).contains("advisory"))
    }

    @Test
    fun `LLM includes advisory when present`() {
        val output = ConvergeOutput.Intersect(ConvergeIntersectOutput(listOf(actNowEdge), 0, null, advisory = "narrow with --scope=prod"))

        assertTrue(ConvergeFormatter.formatLlm(output).contains("advisory: narrow with --scope=prod"))
    }

    @Test
    fun `JSON intersect output includes mode, edges, and verdict labels`() {
        val output = ConvergeOutput.Intersect(ConvergeIntersectOutput(listOf(actNowEdge), 0, null))

        val json = ConvergeFormatter.formatJson(output)

        assertTrue(json.contains("\"mode\":\"intersect\""))
        assertTrue(json.contains("\"verdict\":\"ACT NOW\""))
        assertTrue(json.contains("\"couplingDegree\":75"))
    }

    @Test
    fun `JSON omits couplingDegree when null`() {
        val output = ConvergeOutput.Intersect(ConvergeIntersectOutput(listOf(latentEdge), 0, null))

        val json = ConvergeFormatter.formatJson(output)

        assertTrue(!json.contains("couplingDegree"))
    }

    @Test
    fun `LLM intersect output appends interpretation`() {
        val output = ConvergeOutput.Intersect(ConvergeIntersectOutput(listOf(actNowEdge), 0, null))

        val llm = ConvergeFormatter.formatLlm(output)

        assertTrue(llm.contains("ACT NOW com.example.api<->com.example.service"))
        assertTrue(llm.contains(ConvergeFormatter.INTERSECT_INTERPRETATION))
    }

    @Test
    fun `risk mode TEXT is a ranked list`() {
        val entries = listOf(
            ConvergeRiskEntry(ClassName("com.example.Foo"), "src/Foo.kt", 10, 5, 30, 1500L),
            ConvergeRiskEntry(ClassName("com.example.Bar"), "src/Bar.kt", 2, 1, null, 2L),
        )
        val output = ConvergeOutput.Risk(ConvergeRiskOutput(entries, null))

        val text = ConvergeFormatter.format(output)

        assertTrue(text.startsWith("1. com.example.Foo (src/Foo.kt)  risk=1500"))
        assertTrue(text.contains("2. com.example.Bar"))
    }

    @Test
    fun `risk mode JSON includes entries`() {
        val entries = listOf(ConvergeRiskEntry(ClassName("com.example.Foo"), "src/Foo.kt", 10, 5, 30, 1500L))
        val output = ConvergeOutput.Risk(ConvergeRiskOutput(entries, null))

        val json = ConvergeFormatter.formatJson(output)

        assertTrue(json.contains("\"mode\":\"risk\""))
        assertTrue(json.contains("\"riskScore\":1500"))
    }

    @Test
    fun `risk mode LLM appends interpretation`() {
        val entries = listOf(ConvergeRiskEntry(ClassName("com.example.Foo"), "src/Foo.kt", 10, 5, 30, 1500L))
        val output = ConvergeOutput.Risk(ConvergeRiskOutput(entries, null))

        val llm = ConvergeFormatter.formatLlm(output)

        assertTrue(llm.contains(ConvergeFormatter.RISK_INTERPRETATION))
    }
}
