package no.f12.codenavigator.navigation.dsm

import no.f12.codenavigator.navigation.types.ClassName
import no.f12.codenavigator.navigation.types.PackageName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TypeAffinityBuilderTest {

    // [TEST] Type with single consumer package is flagged as single-owner
    @Test
    fun `type with single consumer package is flagged as single-owner`() {
        val targetPackage = PackageName("com.app.domain")
        val deps = listOf(
            PackageDependency(PackageName("com.app.feature"), targetPackage, ClassName("com.app.feature.Service"), ClassName("com.app.domain.Order")),
        )

        val result = TypeAffinityBuilder.analyze(deps, targetPackage)

        assertEquals(1, result.singleOwnerTypes.size)
        assertEquals(ClassName("com.app.domain.Order"), result.singleOwnerTypes[0].type)
        assertEquals(PackageName("com.app.feature"), result.singleOwnerTypes[0].ownerPackage)
    }

    // [TEST] Type with multiple consumer packages is flagged as shared
    @Test
    fun `type with multiple consumer packages is flagged as shared`() {
        val targetPackage = PackageName("com.app.domain")
        val deps = listOf(
            PackageDependency(PackageName("com.app.feature1"), targetPackage, ClassName("com.app.feature1.Service"), ClassName("com.app.domain.UserId")),
            PackageDependency(PackageName("com.app.feature2"), targetPackage, ClassName("com.app.feature2.Handler"), ClassName("com.app.domain.UserId")),
        )

        val result = TypeAffinityBuilder.analyze(deps, targetPackage)

        assertEquals(0, result.singleOwnerTypes.size)
        assertEquals(1, result.sharedTypes.size)
        assertEquals(ClassName("com.app.domain.UserId"), result.sharedTypes[0].type)
        assertEquals(setOf(PackageName("com.app.feature1"), PackageName("com.app.feature2")), result.sharedTypes[0].consumerPackages)
    }

    // [TEST] Type with no consumers outside its own package is excluded
    @Test
    fun `type with no consumers outside its own package is excluded`() {
        val targetPackage = PackageName("com.app.domain")
        val deps = listOf(
            // Only internal dependency within the target package
            PackageDependency(targetPackage, targetPackage, ClassName("com.app.domain.OrderService"), ClassName("com.app.domain.Order")),
        )

        val result = TypeAffinityBuilder.analyze(deps, targetPackage)

        assertTrue(result.singleOwnerTypes.isEmpty())
        assertTrue(result.sharedTypes.isEmpty())
    }

    // [TEST] Transitive port check: secondary consumer that only serves primary is not counted as separate owner
    @Test
    fun `secondary consumer that only serves primary is not counted as separate owner`() {
        val targetPackage = PackageName("com.app.domain")
        // passwordreset uses PasswordResetSession directly
        // cache also uses PasswordResetSession, BUT cache is only called by passwordreset
        val deps = listOf(
            PackageDependency(PackageName("com.app.passwordreset"), targetPackage, ClassName("com.app.passwordreset.ResetService"), ClassName("com.app.domain.PasswordResetSession")),
            PackageDependency(PackageName("com.app.cache"), targetPackage, ClassName("com.app.cache.Cache"), ClassName("com.app.domain.PasswordResetSession")),
            // cache is only called from passwordreset (making it a port for passwordreset)
            PackageDependency(PackageName("com.app.passwordreset"), PackageName("com.app.cache"), ClassName("com.app.passwordreset.ResetService"), ClassName("com.app.cache.Cache")),
        )

        val result = TypeAffinityBuilder.analyze(deps, targetPackage)

        assertEquals(1, result.singleOwnerTypes.size)
        assertEquals(ClassName("com.app.domain.PasswordResetSession"), result.singleOwnerTypes[0].type)
        assertEquals(PackageName("com.app.passwordreset"), result.singleOwnerTypes[0].ownerPackage)
    }

    // [TEST] Ring impact: moving type into consumer package reduces consumer's ring
    @Test
    fun `ring impact calculated when moving type reduces consumer ring`() {
        val targetPackage = PackageName("com.app.domain")
        // domain is ring 0, service depends on domain (ring 1), feature depends on service+domain (ring 2)
        // feature's only dep on domain is through FeatureState — if we move FeatureState into feature,
        // feature would only depend on service (ring 2 stays? no — ring = max(deps)+1 = 1+1=2 unchanged)
        // Better scenario: feature depends ONLY on domain directly
        // feature -> domain (ring 1). If FeatureState moves into feature, feature has no cross-package deps -> ring 0
        val deps = listOf(
            PackageDependency(PackageName("com.app.feature"), targetPackage, ClassName("com.app.feature.Handler"), ClassName("com.app.domain.FeatureState")),
        )

        val result = TypeAffinityBuilder.analyze(deps, targetPackage)

        assertEquals(1, result.singleOwnerTypes.size)
        // feature was ring 1 (depends on domain ring 0). After move, feature has no external deps -> ring 0.
        // Ring impact = 1 - 0 = 1 (drop of 1)
        assertEquals(1, result.singleOwnerTypes[0].ringImpact)
    }

    // [TEST] Ring impact: type that doesn't change ring has zero impact
    @Test
    fun `ring impact is zero when type is not the only dep on target package`() {
        val targetPackage = PackageName("com.app.domain")
        // feature depends on domain via TWO types — removing one still leaves the dependency
        val deps = listOf(
            PackageDependency(PackageName("com.app.feature"), targetPackage, ClassName("com.app.feature.Handler"), ClassName("com.app.domain.FeatureState")),
            PackageDependency(PackageName("com.app.feature"), targetPackage, ClassName("com.app.feature.Handler"), ClassName("com.app.domain.SharedThing")),
            // SharedThing is also used by another package (so FeatureState is the single-owner candidate)
            PackageDependency(PackageName("com.app.other"), targetPackage, ClassName("com.app.other.X"), ClassName("com.app.domain.SharedThing")),
        )

        val result = TypeAffinityBuilder.analyze(deps, targetPackage)

        val featureState = result.singleOwnerTypes.find { it.type == ClassName("com.app.domain.FeatureState") }
        assertEquals(0, featureState?.ringImpact)
    }

    // [TEST] Results are sorted by ring impact (biggest drop first)
    @Test
    fun `results sorted by ring impact descending`() {
        val targetPackage = PackageName("com.app.domain")
        // Two single-owner types: one with ring impact 1, one with ring impact 0
        val deps = listOf(
            // featureA -> domain only via TypeA (moving removes the dep entirely -> ring drops)
            PackageDependency(PackageName("com.app.featureA"), targetPackage, ClassName("com.app.featureA.A"), ClassName("com.app.domain.TypeA")),
            // featureB -> domain via TypeB AND SharedType (moving TypeB doesn't remove the dep)
            PackageDependency(PackageName("com.app.featureB"), targetPackage, ClassName("com.app.featureB.B"), ClassName("com.app.domain.TypeB")),
            PackageDependency(PackageName("com.app.featureB"), targetPackage, ClassName("com.app.featureB.B"), ClassName("com.app.domain.SharedType")),
            PackageDependency(PackageName("com.app.other"), targetPackage, ClassName("com.app.other.X"), ClassName("com.app.domain.SharedType")),
        )

        val result = TypeAffinityBuilder.analyze(deps, targetPackage)

        assertEquals(2, result.singleOwnerTypes.size)
        assertEquals(ClassName("com.app.domain.TypeA"), result.singleOwnerTypes[0].type)
        assertEquals(1, result.singleOwnerTypes[0].ringImpact)
        assertEquals(ClassName("com.app.domain.TypeB"), result.singleOwnerTypes[1].type)
        assertEquals(0, result.singleOwnerTypes[1].ringImpact)
    }

    // [TEST] Threshold parameter: threshold=2 allows up to 2 consumer domains
    @Test
    fun `threshold parameter allows multiple consumer domains`() {
        val targetPackage = PackageName("com.app.domain")
        val deps = listOf(
            PackageDependency(PackageName("com.app.feature1"), targetPackage, ClassName("com.app.feature1.A"), ClassName("com.app.domain.Order")),
            PackageDependency(PackageName("com.app.feature2"), targetPackage, ClassName("com.app.feature2.B"), ClassName("com.app.domain.Order")),
        )

        val result = TypeAffinityBuilder.analyze(deps, targetPackage, threshold = 2)

        assertEquals(1, result.singleOwnerTypes.size)
        assertEquals(0, result.sharedTypes.size)
    }

    // [TEST] Empty package produces empty result
    @Test
    fun `empty dependencies produces empty result`() {
        val result = TypeAffinityBuilder.analyze(emptyList(), PackageName("com.app.domain"))

        assertTrue(result.singleOwnerTypes.isEmpty())
        assertTrue(result.sharedTypes.isEmpty())
    }
}
