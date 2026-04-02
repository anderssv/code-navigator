package no.f12.codenavigator.navigation

import no.f12.codenavigator.navigation.dsm.ClassKind
import no.f12.codenavigator.navigation.dsm.IntegrationStrength
import no.f12.codenavigator.navigation.dsm.PackageDependency
import no.f12.codenavigator.navigation.dsm.StrengthClassifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StrengthClassifierTest {

    @Test
    fun `dependency on interface is CONTRACT`() {
        val deps = listOf(
            PackageDependency(
                PackageName("com.app.api"), PackageName("com.app.domain"),
                ClassName("com.app.api.Controller"), ClassName("com.app.domain.Repository"),
            ),
        )
        val registry = mapOf(
            ClassName("com.app.domain.Repository") to ClassKind.INTERFACE,
        )

        val result = StrengthClassifier.classify(deps, registry)

        assertEquals(1, result.entries.size)
        assertEquals(IntegrationStrength.CONTRACT, result.entries[0].strength)
    }

    @Test
    fun `dependency on abstract class is CONTRACT`() {
        val deps = listOf(
            PackageDependency(
                PackageName("com.app.api"), PackageName("com.app.domain"),
                ClassName("com.app.api.Controller"), ClassName("com.app.domain.AbstractService"),
            ),
        )
        val registry = mapOf(
            ClassName("com.app.domain.AbstractService") to ClassKind.ABSTRACT,
        )

        val result = StrengthClassifier.classify(deps, registry)

        assertEquals(IntegrationStrength.CONTRACT, result.entries[0].strength)
    }

    @Test
    fun `dependency on data class is MODEL`() {
        val deps = listOf(
            PackageDependency(
                PackageName("com.app.api"), PackageName("com.app.domain"),
                ClassName("com.app.api.Controller"), ClassName("com.app.domain.Order"),
            ),
        )
        val registry = mapOf(
            ClassName("com.app.domain.Order") to ClassKind.DATA_CLASS,
        )

        val result = StrengthClassifier.classify(deps, registry)

        assertEquals(IntegrationStrength.MODEL, result.entries[0].strength)
    }

    @Test
    fun `dependency on record is MODEL`() {
        val deps = listOf(
            PackageDependency(
                PackageName("com.app.api"), PackageName("com.app.domain"),
                ClassName("com.app.api.Controller"), ClassName("com.app.domain.Event"),
            ),
        )
        val registry = mapOf(
            ClassName("com.app.domain.Event") to ClassKind.RECORD,
        )

        val result = StrengthClassifier.classify(deps, registry)

        assertEquals(IntegrationStrength.MODEL, result.entries[0].strength)
    }

    @Test
    fun `dependency on annotated model is MODEL`() {
        val deps = listOf(
            PackageDependency(
                PackageName("com.app.api"), PackageName("com.app.domain"),
                ClassName("com.app.api.Controller"), ClassName("com.app.domain.Owner"),
            ),
        )
        val registry = mapOf(
            ClassName("com.app.domain.Owner") to ClassKind.ANNOTATED_MODEL,
        )

        val result = StrengthClassifier.classify(deps, registry)

        assertEquals(IntegrationStrength.MODEL, result.entries[0].strength)
        assertEquals(1, result.entries[0].modelCount)
    }

    @Test
    fun `dependency on concrete class is FUNCTIONAL`() {
        val deps = listOf(
            PackageDependency(
                PackageName("com.app.api"), PackageName("com.app.service"),
                ClassName("com.app.api.Controller"), ClassName("com.app.service.OrderService"),
            ),
        )
        val registry = mapOf(
            ClassName("com.app.service.OrderService") to ClassKind.CONCRETE,
        )

        val result = StrengthClassifier.classify(deps, registry)

        assertEquals(IntegrationStrength.FUNCTIONAL, result.entries[0].strength)
    }

    @Test
    fun `unknown target class is counted as unknown not functional`() {
        val deps = listOf(
            PackageDependency(
                PackageName("com.app.api"), PackageName("com.app.external"),
                ClassName("com.app.api.Controller"), ClassName("com.app.external.Client"),
            ),
        )
        val registry = emptyMap<ClassName, ClassKind>()

        val result = StrengthClassifier.classify(deps, registry)

        val entry = result.entries[0]
        assertEquals(0, entry.functionalCount)
        assertEquals(1, entry.unknownCount)
        assertEquals(1, entry.totalDeps)
    }

    @Test
    fun `all-unknown pair has aggregate strength CONTRACT`() {
        val deps = listOf(
            PackageDependency(
                PackageName("com.app.api"), PackageName("com.app.external"),
                ClassName("com.app.api.Controller"), ClassName("com.app.external.Client"),
            ),
            PackageDependency(
                PackageName("com.app.api"), PackageName("com.app.external"),
                ClassName("com.app.api.Handler"), ClassName("com.app.external.Util"),
            ),
        )
        val registry = emptyMap<ClassName, ClassKind>()

        val result = StrengthClassifier.classify(deps, registry)

        val entry = result.entries[0]
        assertEquals(IntegrationStrength.CONTRACT, entry.strength)
        assertEquals(0, entry.contractCount)
        assertEquals(0, entry.modelCount)
        assertEquals(0, entry.functionalCount)
        assertEquals(2, entry.unknownCount)
    }

    @Test
    fun `mixed known and unknown deps aggregates strongest known and tracks unknownCount`() {
        val deps = listOf(
            PackageDependency(
                PackageName("com.app.api"), PackageName("com.app.domain"),
                ClassName("com.app.api.Controller"), ClassName("com.app.domain.Repository"),
            ),
            PackageDependency(
                PackageName("com.app.api"), PackageName("com.app.domain"),
                ClassName("com.app.api.Controller"), ClassName("com.app.domain.UnknownExternal"),
            ),
        )
        val registry = mapOf(
            ClassName("com.app.domain.Repository") to ClassKind.INTERFACE,
        )

        val result = StrengthClassifier.classify(deps, registry)

        val entry = result.entries[0]
        assertEquals(IntegrationStrength.CONTRACT, entry.strength)
        assertEquals(1, entry.contractCount)
        assertEquals(0, entry.modelCount)
        assertEquals(0, entry.functionalCount)
        assertEquals(1, entry.unknownCount)
        assertEquals(2, entry.totalDeps)
    }

    @Test
    fun `all-known deps have unknownCount zero`() {
        val deps = listOf(
            PackageDependency(
                PackageName("com.app.api"), PackageName("com.app.domain"),
                ClassName("com.app.api.Controller"), ClassName("com.app.domain.Order"),
            ),
        )
        val registry = mapOf(
            ClassName("com.app.domain.Order") to ClassKind.DATA_CLASS,
        )

        val result = StrengthClassifier.classify(deps, registry)

        assertEquals(0, result.entries[0].unknownCount)
    }

    @Test
    fun `multiple edges between same package pair are aggregated with strongest level`() {
        val deps = listOf(
            PackageDependency(
                PackageName("com.app.api"), PackageName("com.app.domain"),
                ClassName("com.app.api.Controller"), ClassName("com.app.domain.Repository"),
            ),
            PackageDependency(
                PackageName("com.app.api"), PackageName("com.app.domain"),
                ClassName("com.app.api.Controller"), ClassName("com.app.domain.Order"),
            ),
            PackageDependency(
                PackageName("com.app.api"), PackageName("com.app.domain"),
                ClassName("com.app.api.Handler"), ClassName("com.app.domain.OrderService"),
            ),
        )
        val registry = mapOf(
            ClassName("com.app.domain.Repository") to ClassKind.INTERFACE,
            ClassName("com.app.domain.Order") to ClassKind.DATA_CLASS,
            ClassName("com.app.domain.OrderService") to ClassKind.CONCRETE,
        )

        val result = StrengthClassifier.classify(deps, registry)

        assertEquals(1, result.entries.size)
        val entry = result.entries[0]
        assertEquals(IntegrationStrength.FUNCTIONAL, entry.strength)
        assertEquals(1, entry.contractCount)
        assertEquals(1, entry.modelCount)
        assertEquals(1, entry.functionalCount)
        assertEquals(3, entry.totalDeps)
    }

    @Test
    fun `results are sorted by strength descending then by total deps descending`() {
        val deps = listOf(
            PackageDependency(
                PackageName("com.app.a"), PackageName("com.app.b"),
                ClassName("com.app.a.A"), ClassName("com.app.b.IFace"),
            ),
            PackageDependency(
                PackageName("com.app.c"), PackageName("com.app.d"),
                ClassName("com.app.c.C"), ClassName("com.app.d.Service"),
            ),
            PackageDependency(
                PackageName("com.app.e"), PackageName("com.app.f"),
                ClassName("com.app.e.E"), ClassName("com.app.f.Model"),
            ),
        )
        val registry = mapOf(
            ClassName("com.app.b.IFace") to ClassKind.INTERFACE,
            ClassName("com.app.d.Service") to ClassKind.CONCRETE,
            ClassName("com.app.f.Model") to ClassKind.DATA_CLASS,
        )

        val result = StrengthClassifier.classify(deps, registry)

        assertEquals(3, result.entries.size)
        assertEquals(IntegrationStrength.FUNCTIONAL, result.entries[0].strength)
        assertEquals(IntegrationStrength.MODEL, result.entries[1].strength)
        assertEquals(IntegrationStrength.CONTRACT, result.entries[2].strength)
    }

    @Test
    fun `empty dependency list produces empty result`() {
        val result = StrengthClassifier.classify(emptyList(), emptyMap())

        assertTrue(result.entries.isEmpty())
    }

    @Test
    fun `top parameter limits results`() {
        val deps = listOf(
            PackageDependency(
                PackageName("com.app.a"), PackageName("com.app.b"),
                ClassName("com.app.a.A"), ClassName("com.app.b.B"),
            ),
            PackageDependency(
                PackageName("com.app.c"), PackageName("com.app.d"),
                ClassName("com.app.c.C"), ClassName("com.app.d.D"),
            ),
        )
        val registry = mapOf(
            ClassName("com.app.b.B") to ClassKind.CONCRETE,
            ClassName("com.app.d.D") to ClassKind.CONCRETE,
        )

        val result = StrengthClassifier.classify(deps, registry, top = 1)

        assertEquals(1, result.entries.size)
    }

    @Test
    fun `package filter narrows to matching source packages`() {
        val deps = listOf(
            PackageDependency(
                PackageName("com.app.api"), PackageName("com.app.domain"),
                ClassName("com.app.api.Controller"), ClassName("com.app.domain.Order"),
            ),
            PackageDependency(
                PackageName("com.other.service"), PackageName("com.other.model"),
                ClassName("com.other.service.Svc"), ClassName("com.other.model.Dto"),
            ),
        )
        val registry = mapOf(
            ClassName("com.app.domain.Order") to ClassKind.DATA_CLASS,
            ClassName("com.other.model.Dto") to ClassKind.DATA_CLASS,
        )

        val result = StrengthClassifier.classify(deps, registry, packageFilter = PackageName("com.app"))

        assertEquals(1, result.entries.size)
        assertEquals(PackageName("com.app.api"), result.entries[0].source)
    }

    @Test
    fun `package filter includes edges where filtered package is the target`() {
        val deps = listOf(
            PackageDependency(
                PackageName("com.app.api"), PackageName("com.app.domain"),
                ClassName("com.app.api.Controller"), ClassName("com.app.domain.Order"),
            ),
            PackageDependency(
                PackageName("com.app.domain"), PackageName("com.app.api"),
                ClassName("com.app.domain.OrderFactory"), ClassName("com.app.api.ApiUtils"),
            ),
            PackageDependency(
                PackageName("com.other.service"), PackageName("com.other.model"),
                ClassName("com.other.service.Svc"), ClassName("com.other.model.Dto"),
            ),
        )
        val registry = mapOf(
            ClassName("com.app.domain.Order") to ClassKind.DATA_CLASS,
            ClassName("com.app.api.ApiUtils") to ClassKind.CONCRETE,
            ClassName("com.other.model.Dto") to ClassKind.DATA_CLASS,
        )

        val result = StrengthClassifier.classify(deps, registry, packageFilter = PackageName("com.app.api"))

        assertEquals(2, result.entries.size)
        val pairs = result.entries.map { it.source to it.target }.toSet()
        assertTrue(pairs.contains(PackageName("com.app.api") to PackageName("com.app.domain")))
        assertTrue(pairs.contains(PackageName("com.app.domain") to PackageName("com.app.api")))
    }
}
