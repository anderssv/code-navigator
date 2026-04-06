package no.f12.codenavigator.navigation.dsm

import no.f12.codenavigator.navigation.core.ClassName
import no.f12.codenavigator.navigation.core.PackageName
import kotlin.test.Test
import kotlin.test.assertEquals

class LayerCheckerTest {

    private val config = LayerConfig.parse("""
    {
      "layers": [
        { "name": "http", "patterns": ["*Controller", "*Routes"] },
        { "name": "service", "patterns": ["*Service"] },
        { "name": "adapter", "patterns": ["*Repository", "*Client"] },
        { "name": "domain", "patterns": ["*"] }
      ]
    }
    """.trimIndent())

    @Test
    fun `no violations when all dependencies go inward`() {
        val deps = listOf(
            dep("com.example.api.OrderController", "com.example.services.OrderService"),
            dep("com.example.services.OrderService", "com.example.domain.Order"),
            dep("com.example.services.OrderService", "com.example.infra.OrderRepository"),
        )

        val result = LayerChecker.check(config, deps)

        assertEquals(0, result.violations.size)
    }

    @Test
    fun `outward dependency is a violation`() {
        val deps = listOf(
            dep("com.example.domain.Order", "com.example.services.OrderService"),
        )

        val result = LayerChecker.check(config, deps)

        assertEquals(1, result.violations.size)
        val v = result.violations[0]
        assertEquals(ViolationType.OUTWARD, v.type)
        assertEquals("domain", v.sourceLayer)
        assertEquals("service", v.targetLayer)
        assertEquals(ClassName("com.example.domain.Order"), v.sourceClass)
        assertEquals(ClassName("com.example.services.OrderService"), v.targetClass)
    }

    @Test
    fun `peer dependency is a violation when peerLimit is zero`() {
        val deps = listOf(
            dep("com.example.services.OrderService", "com.example.services.CustomerService"),
        )

        val result = LayerChecker.check(config, deps)

        assertEquals(1, result.violations.size)
        val v = result.violations[0]
        assertEquals(ViolationType.PEER, v.type)
        assertEquals("service", v.sourceLayer)
        assertEquals("service", v.targetLayer)
    }

    @Test
    fun `peer dependency is allowed when within peerLimit`() {
        val configWithLimit = LayerConfig.parse("""
        {
          "layers": [
            { "name": "service", "patterns": ["*Service"], "peerLimit": 2 },
            { "name": "domain", "patterns": ["*"] }
          ]
        }
        """.trimIndent())

        val deps = listOf(
            dep("com.example.services.OrderService", "com.example.services.CustomerService"),
        )

        val result = LayerChecker.check(configWithLimit, deps)

        assertEquals(0, result.violations.size)
    }

    @Test
    fun `peer dependency exceeding peerLimit is a violation`() {
        val configWithLimit = LayerConfig.parse("""
        {
          "layers": [
            { "name": "service", "patterns": ["*Service"], "peerLimit": 1 },
            { "name": "domain", "patterns": ["*"] }
          ]
        }
        """.trimIndent())

        val deps = listOf(
            dep("com.example.services.OrderService", "com.example.services.CustomerService"),
            dep("com.example.services.OrderService", "com.example.services.PaymentService"),
        )

        val result = LayerChecker.check(configWithLimit, deps)

        val peerViolations = result.violations.filter { it.type == ViolationType.PEER }
        assertEquals(2, peerViolations.size)
        assertEquals(ClassName("com.example.services.OrderService"), peerViolations[0].sourceClass)
    }

    @Test
    fun `dependencies involving unmatched classes are not violations`() {
        val configNoWildcard = LayerConfig.parse("""
        {
          "layers": [
            { "name": "service", "patterns": ["*Service"] }
          ]
        }
        """.trimIndent())

        val deps = listOf(
            dep("com.example.util.Helper", "com.example.common.Utils"),
        )

        val result = LayerChecker.check(configNoWildcard, deps)

        assertEquals(0, result.violations.size)
    }

    @Test
    fun `dependency from unmatched to matched class is not a violation`() {
        val configNoWildcard = LayerConfig.parse("""
        {
          "layers": [
            { "name": "service", "patterns": ["*Service"] }
          ]
        }
        """.trimIndent())

        val deps = listOf(
            dep("com.example.util.Helper", "com.example.services.OrderService"),
        )

        val result = LayerChecker.check(configNoWildcard, deps)

        assertEquals(0, result.violations.size)
    }

    @Test
    fun `dependency from matched to unmatched class is not a violation`() {
        val configNoWildcard = LayerConfig.parse("""
        {
          "layers": [
            { "name": "service", "patterns": ["*Service"] }
          ]
        }
        """.trimIndent())

        val deps = listOf(
            dep("com.example.services.OrderService", "com.example.util.Helper"),
        )

        val result = LayerChecker.check(configNoWildcard, deps)

        assertEquals(0, result.violations.size)
    }

    @Test
    fun `no dependencies means no violations`() {
        val result = LayerChecker.check(config, emptyList())

        assertEquals(0, result.violations.size)
        assertEquals(emptySet(), result.unassignedClasses)
    }

    @Test
    fun `reports unassigned classes`() {
        val configNoWildcard = LayerConfig.parse("""
        {
          "layers": [
            { "name": "service", "patterns": ["*Service"] }
          ]
        }
        """.trimIndent())

        val deps = listOf(
            dep("com.example.util.Helper", "com.example.services.OrderService"),
        )

        val result = LayerChecker.check(configNoWildcard, deps)

        assertEquals(setOf(ClassName("com.example.util.Helper")), result.unassignedClasses)
    }

    @Test
    fun `adapter depending on domain is allowed`() {
        val deps = listOf(
            dep("com.example.infra.OrderRepository", "com.example.domain.Order"),
        )

        val result = LayerChecker.check(config, deps)

        assertEquals(0, result.violations.size)
    }

    @Test
    fun `domain depending on adapter is an outward violation`() {
        val deps = listOf(
            dep("com.example.domain.Order", "com.example.infra.OrderRepository"),
        )

        val result = LayerChecker.check(config, deps)

        assertEquals(1, result.violations.size)
        assertEquals(ViolationType.OUTWARD, result.violations[0].type)
        assertEquals("domain", result.violations[0].sourceLayer)
        assertEquals("adapter", result.violations[0].targetLayer)
    }

    @Test
    fun `http depending on adapter is allowed`() {
        val deps = listOf(
            dep("com.example.api.OrderController", "com.example.infra.OrderRepository"),
        )

        val result = LayerChecker.check(config, deps)

        assertEquals(0, result.violations.size)
    }

    @Test
    fun `peerLimit is evaluated per class independently`() {
        val configWithLimit = LayerConfig.parse("""
        {
          "layers": [
            { "name": "service", "patterns": ["*Service"], "peerLimit": 1 },
            { "name": "domain", "patterns": ["*"] }
          ]
        }
        """.trimIndent())

        val deps = listOf(
            dep("com.example.services.OrderService", "com.example.services.CustomerService"),
            dep("com.example.services.OrderService", "com.example.services.PaymentService"),
            dep("com.example.services.CustomerService", "com.example.services.NotificationService"),
        )

        val result = LayerChecker.check(configWithLimit, deps)

        val peerViolations = result.violations.filter { it.type == ViolationType.PEER }
        val orderViolations = peerViolations.filter { it.sourceClass == ClassName("com.example.services.OrderService") }
        val customerViolations = peerViolations.filter { it.sourceClass == ClassName("com.example.services.CustomerService") }

        assertEquals(2, orderViolations.size, "OrderService has 2 peers, exceeds limit of 1")
        assertEquals(0, customerViolations.size, "CustomerService has 1 peer, within limit of 1")
    }

    @Test
    fun `test class is assigned to same layer as its production counterpart`() {
        val deps = listOf(
            dep("com.example.api.OrderControllerTest", "com.example.services.OrderService"),
            dep("com.example.api.OrderControllerTest", "com.example.domain.Order"),
        )

        val result = LayerChecker.check(config, deps)

        assertEquals(0, result.violations.size, "OrderControllerTest is in http layer, deps on service and domain are allowed")
    }

    @Test
    fun `test class depending on higher layer is still a violation`() {
        val deps = listOf(
            dep("com.example.services.OrderServiceTest", "com.example.api.OrderController"),
        )

        val result = LayerChecker.check(config, deps)

        assertEquals(1, result.violations.size)
        val v = result.violations[0]
        assertEquals(ViolationType.OUTWARD, v.type)
        assertEquals("service", v.sourceLayer)
        assertEquals("http", v.targetLayer)
    }

    @Test
    fun `peerLimit of -1 means unlimited peer dependencies`() {
        val configUnlimited = LayerConfig.parse("""
        {
          "layers": [
            { "name": "domain", "patterns": ["*"], "peerLimit": -1 }
          ]
        }
        """.trimIndent())

        val deps = listOf(
            dep("com.example.domain.Order", "com.example.domain.Customer"),
            dep("com.example.domain.Order", "com.example.domain.Product"),
            dep("com.example.domain.Order", "com.example.domain.Payment"),
            dep("com.example.domain.Order", "com.example.domain.Shipping"),
        )

        val result = LayerChecker.check(configUnlimited, deps)

        assertEquals(0, result.violations.size)
    }

    // --- testInfrastructure ---

    private val configWithTestInfra = LayerConfig.parse("""
    {
      "layers": [
        { "name": "wiring", "patterns": ["*Dependencies", "*TestContext"], "testInfrastructure": true },
        { "name": "http", "patterns": ["*Controller", "*Routes"] },
        { "name": "service", "patterns": ["*Service"] },
        { "name": "domain", "patterns": ["*"] }
      ]
    }
    """.trimIndent())

    @Test
    fun `test class depending on testInfrastructure layer is not an outward violation`() {
        val deps = listOf(
            dep("com.example.services.OrderServiceTest", "com.example.wiring.AppDependencies"),
        )

        val result = LayerChecker.check(configWithTestInfra, deps)

        assertEquals(0, result.violations.size)
    }

    @Test
    fun `production class depending on testInfrastructure layer is still an outward violation`() {
        val deps = listOf(
            dep("com.example.services.OrderService", "com.example.wiring.AppDependencies"),
        )

        val result = LayerChecker.check(configWithTestInfra, deps)

        assertEquals(1, result.violations.size)
        assertEquals(ViolationType.OUTWARD, result.violations[0].type)
        assertEquals("service", result.violations[0].sourceLayer)
        assertEquals("wiring", result.violations[0].targetLayer)
    }

    @Test
    fun `test class depending on non-testInfrastructure higher layer is still an outward violation`() {
        val deps = listOf(
            dep("com.example.services.OrderServiceTest", "com.example.api.OrderController"),
        )

        val result = LayerChecker.check(configWithTestInfra, deps)

        assertEquals(1, result.violations.size)
        assertEquals(ViolationType.OUTWARD, result.violations[0].type)
        assertEquals("service", result.violations[0].sourceLayer)
        assertEquals("http", result.violations[0].targetLayer)
    }

    private fun dep(
        sourceClass: String,
        targetClass: String,
    ): PackageDependency {
        val src = ClassName(sourceClass)
        val tgt = ClassName(targetClass)
        return PackageDependency(
            sourcePackage = src.packageName(),
            targetPackage = tgt.packageName(),
            sourceClass = src,
            targetClass = tgt,
        )
    }
}
