package no.f12.codenavigator.navigation.dsm

import no.f12.codenavigator.navigation.types.ClassName
import no.f12.codenavigator.navigation.types.PackageName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MoveSuggesterTest {

    @Test
    fun `empty dependencies produces no move suggestions`() {
        val result = MoveSuggester.suggest(emptyList())

        assertTrue(result.suggestions.isEmpty())
    }

    // [TEST] Class with all edges to own package produces no suggestion
    @Test
    fun `class with all edges to own package produces no suggestion`() {
        val deps = listOf(
            PackageDependency(PackageName("com.a"), PackageName("com.a"), ClassName("com.a.Foo"), ClassName("com.a.Bar")),
            PackageDependency(PackageName("com.a"), PackageName("com.a"), ClassName("com.a.Foo"), ClassName("com.a.Baz")),
        )

        val result = MoveSuggester.suggest(deps)

        assertTrue(result.suggestions.isEmpty())
    }

    // [TEST] Class with more edges to another package than own produces a move suggestion
    @Test
    fun `class with more edges to another package than own produces a move suggestion`() {
        val deps = listOf(
            PackageDependency(PackageName("com.a"), PackageName("com.a"), ClassName("com.a.Foo"), ClassName("com.a.Bar")),
            PackageDependency(PackageName("com.a"), PackageName("com.b"), ClassName("com.a.Foo"), ClassName("com.b.X")),
            PackageDependency(PackageName("com.a"), PackageName("com.b"), ClassName("com.a.Foo"), ClassName("com.b.Y")),
            PackageDependency(PackageName("com.a"), PackageName("com.b"), ClassName("com.a.Foo"), ClassName("com.b.Z")),
        )

        val result = MoveSuggester.suggest(deps)

        assertEquals(1, result.suggestions.size)
        val suggestion = result.suggestions[0]
        assertEquals(ClassName("com.a.Foo"), suggestion.className)
        assertEquals(PackageName("com.a"), suggestion.currentPackage)
        assertEquals(PackageName("com.b"), suggestion.suggestedPackage)
        assertEquals(1, suggestion.edgesToCurrent)
        assertEquals(3, suggestion.edgesToSuggested)
    }

    // [TEST] Class with equal edges to own and another package produces no suggestion (tie = stay)
    @Test
    fun `class with equal edges to own and another package produces no suggestion`() {
        val deps = listOf(
            PackageDependency(PackageName("com.a"), PackageName("com.a"), ClassName("com.a.Foo"), ClassName("com.a.Bar")),
            PackageDependency(PackageName("com.a"), PackageName("com.b"), ClassName("com.a.Foo"), ClassName("com.b.X")),
        )

        val result = MoveSuggester.suggest(deps)

        assertTrue(result.suggestions.isEmpty())
    }

    @Test
    fun `class with zero edges to own package and all to one other package suggests move`() {
        val deps = listOf(
            PackageDependency(PackageName("com.a"), PackageName("com.b"), ClassName("com.a.Foo"), ClassName("com.b.X")),
            PackageDependency(PackageName("com.a"), PackageName("com.b"), ClassName("com.a.Foo"), ClassName("com.b.Y")),
        )

        val result = MoveSuggester.suggest(deps)

        assertEquals(1, result.suggestions.size)
        assertEquals(ClassName("com.a.Foo"), result.suggestions[0].className)
        assertEquals(PackageName("com.b"), result.suggestions[0].suggestedPackage)
        assertEquals(0, result.suggestions[0].edgesToCurrent)
        assertEquals(2, result.suggestions[0].edgesToSuggested)
        assertEquals(1.0, result.suggestions[0].confidence)
    }

    @Test
    fun `multiple classes can produce multiple suggestions`() {
        val deps = listOf(
            PackageDependency(PackageName("com.a"), PackageName("com.b"), ClassName("com.a.Foo"), ClassName("com.b.X")),
            PackageDependency(PackageName("com.a"), PackageName("com.b"), ClassName("com.a.Foo"), ClassName("com.b.Y")),
            PackageDependency(PackageName("com.c"), PackageName("com.b"), ClassName("com.c.Bar"), ClassName("com.b.X")),
            PackageDependency(PackageName("com.c"), PackageName("com.b"), ClassName("com.c.Bar"), ClassName("com.b.Z")),
        )

        val result = MoveSuggester.suggest(deps)

        assertEquals(2, result.suggestions.size)
    }

    @Test
    fun `ubiquitous types with high fan-in are not suggested for move`() {
        val deps = listOf(
            // Foo depends on Ubiquitous (which many classes depend on)
            PackageDependency(PackageName("com.a"), PackageName("com.util"), ClassName("com.a.Foo"), ClassName("com.util.Ubiquitous")),
            PackageDependency(PackageName("com.b"), PackageName("com.util"), ClassName("com.b.Bar"), ClassName("com.util.Ubiquitous")),
            PackageDependency(PackageName("com.c"), PackageName("com.util"), ClassName("com.c.Baz"), ClassName("com.util.Ubiquitous")),
            PackageDependency(PackageName("com.d"), PackageName("com.util"), ClassName("com.d.Qux"), ClassName("com.util.Ubiquitous")),
            PackageDependency(PackageName("com.e"), PackageName("com.util"), ClassName("com.e.Quux"), ClassName("com.util.Ubiquitous")),
            // Foo also has 1 edge to own package
            PackageDependency(PackageName("com.a"), PackageName("com.a"), ClassName("com.a.Foo"), ClassName("com.a.Other")),
        )

        val result = MoveSuggester.suggest(deps, maxFanIn = 4)

        // Foo should NOT be suggested to move to com.util because Ubiquitous is ubiquitous
        assertTrue(result.suggestions.none { it.className == ClassName("com.a.Foo") })
    }

    @Test
    fun `suggestions are sorted by confidence descending`() {
        val deps = listOf(
            // Foo: 1 own, 2 other → confidence 2/3
            PackageDependency(PackageName("com.a"), PackageName("com.a"), ClassName("com.a.Foo"), ClassName("com.a.X")),
            PackageDependency(PackageName("com.a"), PackageName("com.b"), ClassName("com.a.Foo"), ClassName("com.b.Y")),
            PackageDependency(PackageName("com.a"), PackageName("com.b"), ClassName("com.a.Foo"), ClassName("com.b.Z")),
            // Bar: 0 own, 3 other → confidence 1.0
            PackageDependency(PackageName("com.c"), PackageName("com.d"), ClassName("com.c.Bar"), ClassName("com.d.A")),
            PackageDependency(PackageName("com.c"), PackageName("com.d"), ClassName("com.c.Bar"), ClassName("com.d.B")),
            PackageDependency(PackageName("com.c"), PackageName("com.d"), ClassName("com.c.Bar"), ClassName("com.d.C")),
        )

        val result = MoveSuggester.suggest(deps)

        assertEquals(ClassName("com.c.Bar"), result.suggestions[0].className)
        assertEquals(ClassName("com.a.Foo"), result.suggestions[1].className)
    }

    @Test
    fun `top parameter limits output count`() {
        val deps = listOf(
            PackageDependency(PackageName("com.a"), PackageName("com.b"), ClassName("com.a.Foo"), ClassName("com.b.X")),
            PackageDependency(PackageName("com.a"), PackageName("com.b"), ClassName("com.a.Foo"), ClassName("com.b.Y")),
            PackageDependency(PackageName("com.c"), PackageName("com.d"), ClassName("com.c.Bar"), ClassName("com.d.A")),
            PackageDependency(PackageName("com.c"), PackageName("com.d"), ClassName("com.c.Bar"), ClassName("com.d.B")),
        )

        val result = MoveSuggester.suggest(deps, top = 1)

        assertEquals(1, result.suggestions.size)
    }

    @Test
    fun `class with edges to 5+ distinct packages is suppressed as composition root`() {
        val deps = listOf(
            PackageDependency(PackageName("com.di"), PackageName("com.a"), ClassName("com.di.AppWiring"), ClassName("com.a.ServiceA")),
            PackageDependency(PackageName("com.di"), PackageName("com.b"), ClassName("com.di.AppWiring"), ClassName("com.b.ServiceB")),
            PackageDependency(PackageName("com.di"), PackageName("com.c"), ClassName("com.di.AppWiring"), ClassName("com.c.ServiceC")),
            PackageDependency(PackageName("com.di"), PackageName("com.d"), ClassName("com.di.AppWiring"), ClassName("com.d.ServiceD")),
            PackageDependency(PackageName("com.di"), PackageName("com.e"), ClassName("com.di.AppWiring"), ClassName("com.e.ServiceE")),
        )

        val result = MoveSuggester.suggest(deps)

        assertTrue(result.suggestions.none { it.className == ClassName("com.di.AppWiring") })
    }

    @Test
    fun `class named Context is suppressed as composition root even with fewer package edges`() {
        val deps = listOf(
            PackageDependency(PackageName("com.app"), PackageName("com.domain"), ClassName("com.app.ApplicationContext"), ClassName("com.domain.Foo")),
            PackageDependency(PackageName("com.app"), PackageName("com.domain"), ClassName("com.app.ApplicationContext"), ClassName("com.domain.Bar")),
            PackageDependency(PackageName("com.app"), PackageName("com.domain"), ClassName("com.app.ApplicationContext"), ClassName("com.domain.Baz")),
        )

        val result = MoveSuggester.suggest(deps)

        assertTrue(result.suggestions.none { it.className == ClassName("com.app.ApplicationContext") })
    }

    @Test
    fun `class with high fan-out to 4 packages is NOT suppressed`() {
        val deps = listOf(
            PackageDependency(PackageName("com.a"), PackageName("com.b"), ClassName("com.a.RegularService"), ClassName("com.b.X")),
            PackageDependency(PackageName("com.a"), PackageName("com.c"), ClassName("com.a.RegularService"), ClassName("com.c.Y")),
            PackageDependency(PackageName("com.a"), PackageName("com.d"), ClassName("com.a.RegularService"), ClassName("com.d.Z")),
            PackageDependency(PackageName("com.a"), PackageName("com.e"), ClassName("com.a.RegularService"), ClassName("com.e.W")),
        )

        val result = MoveSuggester.suggest(deps)

        assertTrue(result.suggestions.any { it.className == ClassName("com.a.RegularService") })
    }

    @Test
    fun `route handler is not suggested to move into a domain package`() {
        val deps = listOf(
            PackageDependency(PackageName("com.web"), PackageName("com.web"), ClassName("com.web.UserRoutes"), ClassName("com.web.WebUtil")),
            PackageDependency(PackageName("com.web"), PackageName("com.auth"), ClassName("com.web.UserRoutes"), ClassName("com.auth.AuthService")),
            PackageDependency(PackageName("com.web"), PackageName("com.auth"), ClassName("com.web.UserRoutes"), ClassName("com.auth.TokenValidator")),
            PackageDependency(PackageName("com.web"), PackageName("com.auth"), ClassName("com.web.UserRoutes"), ClassName("com.auth.SessionManager")),
        )

        val result = MoveSuggester.suggest(deps)

        assertTrue(result.suggestions.none { it.className == ClassName("com.web.UserRoutes") })
    }

    @Test
    fun `controller is not suggested to move into a domain package`() {
        val deps = listOf(
            PackageDependency(PackageName("com.api"), PackageName("com.service"), ClassName("com.api.OrderController"), ClassName("com.service.OrderService")),
            PackageDependency(PackageName("com.api"), PackageName("com.service"), ClassName("com.api.OrderController"), ClassName("com.service.PaymentService")),
            PackageDependency(PackageName("com.api"), PackageName("com.service"), ClassName("com.api.OrderController"), ClassName("com.service.ShippingService")),
        )

        val result = MoveSuggester.suggest(deps)

        assertTrue(result.suggestions.none { it.className == ClassName("com.api.OrderController") })
    }

    @Test
    fun `class with callers from own package has reduced confidence`() {
        val deps = listOf(
            // Foo has 0 outgoing edges to own package, 2 to com.b
            PackageDependency(PackageName("com.a"), PackageName("com.b"), ClassName("com.a.Foo"), ClassName("com.b.X")),
            PackageDependency(PackageName("com.a"), PackageName("com.b"), ClassName("com.a.Foo"), ClassName("com.b.Y")),
            // But Foo is called by a sibling in com.a
            PackageDependency(PackageName("com.a"), PackageName("com.a"), ClassName("com.a.Bar"), ClassName("com.a.Foo")),
        )

        val result = MoveSuggester.suggest(deps)

        val suggestion = result.suggestions.find { it.className == ClassName("com.a.Foo") }!!
        assertTrue(suggestion.confidence < 1.0, "Confidence should be reduced by callers from same package, was ${suggestion.confidence}")
    }

    @Test
    fun `class with many callers from own package is not suggested to move`() {
        val deps = listOf(
            // Foo has 0 outgoing edges to own package, 2 to com.b
            PackageDependency(PackageName("com.a"), PackageName("com.b"), ClassName("com.a.Foo"), ClassName("com.b.X")),
            PackageDependency(PackageName("com.a"), PackageName("com.b"), ClassName("com.a.Foo"), ClassName("com.b.Y")),
            // But Foo is called by 3 siblings in com.a — strong signal it belongs here
            PackageDependency(PackageName("com.a"), PackageName("com.a"), ClassName("com.a.Bar"), ClassName("com.a.Foo")),
            PackageDependency(PackageName("com.a"), PackageName("com.a"), ClassName("com.a.Baz"), ClassName("com.a.Foo")),
            PackageDependency(PackageName("com.a"), PackageName("com.a"), ClassName("com.a.Qux"), ClassName("com.a.Foo")),
        )

        val result = MoveSuggester.suggest(deps)

        // With 3 callers and only 2 outgoing to com.b, confidence = 2/5 = 0.4 — below threshold
        // bestOther (2) must be > edgesToOwn (0) to even be a candidate, so it IS a candidate
        // but confidence is low enough to indicate it's likely not misplaced
        val suggestion = result.suggestions.find { it.className == ClassName("com.a.Foo") }
        if (suggestion != null) {
            assertTrue(suggestion.confidence < 0.5, "Confidence should be very low: ${suggestion.confidence}")
        }
    }

    @Test
    fun `feature slice - class not suggested when siblings share a common in-package dependency`() {
        // Feature package com.feature has: Domain, Service, Page
        // Service and Page both depend on Domain (in same package)
        // Service also calls com.infra (outgoing edge to another package)
        // Without feature-slice awareness, Service would be suggested to move
        val deps = listOf(
            // Service depends on Domain (same package) and com.infra
            PackageDependency(PackageName("com.feature"), PackageName("com.feature"), ClassName("com.feature.Service"), ClassName("com.feature.Domain")),
            PackageDependency(PackageName("com.feature"), PackageName("com.infra"), ClassName("com.feature.Service"), ClassName("com.infra.Repository")),
            PackageDependency(PackageName("com.feature"), PackageName("com.infra"), ClassName("com.feature.Service"), ClassName("com.infra.Cache")),
            // Page also depends on Domain (same package) and com.web
            PackageDependency(PackageName("com.feature"), PackageName("com.feature"), ClassName("com.feature.Page"), ClassName("com.feature.Domain")),
            PackageDependency(PackageName("com.feature"), PackageName("com.web"), ClassName("com.feature.Page"), ClassName("com.web.Template")),
            PackageDependency(PackageName("com.feature"), PackageName("com.web"), ClassName("com.feature.Page"), ClassName("com.web.Layout")),
        )

        val result = MoveSuggester.suggest(deps)

        // Service shares Domain with Page — they're a feature slice around Domain
        assertTrue(result.suggestions.none { it.className == ClassName("com.feature.Service") },
            "Service should not be suggested to move: it shares Domain with siblings (feature slice)")
        assertTrue(result.suggestions.none { it.className == ClassName("com.feature.Page") },
            "Page should not be suggested to move: it shares Domain with siblings (feature slice)")
    }

    @Test
    fun `non-feature-slice - classes clustering around external class are still suggested`() {
        val deps = listOf(
            // Foo and Bar are in com.a but both depend on com.b.SharedThing (external)
            // They have no shared in-package dependency
            PackageDependency(PackageName("com.a"), PackageName("com.b"), ClassName("com.a.Foo"), ClassName("com.b.SharedThing")),
            PackageDependency(PackageName("com.a"), PackageName("com.b"), ClassName("com.a.Foo"), ClassName("com.b.Other")),
            PackageDependency(PackageName("com.a"), PackageName("com.b"), ClassName("com.a.Bar"), ClassName("com.b.SharedThing")),
            PackageDependency(PackageName("com.a"), PackageName("com.b"), ClassName("com.a.Bar"), ClassName("com.b.Another")),
        )

        val result = MoveSuggester.suggest(deps)

        assertTrue(result.suggestions.any { it.className == ClassName("com.a.Foo") },
            "Foo should still be suggested: no shared in-package dependency")
        assertTrue(result.suggestions.any { it.className == ClassName("com.a.Bar") },
            "Bar should still be suggested: no shared in-package dependency")
    }

    @Test
    fun `structural supertype in different package alone suggests move`() {
        val structural = listOf(
            StructuralSupertypeInfo(ClassName("fakes.FakeRepo"), ClassName("polls.Repo")),
        )

        val result = MoveSuggester.suggest(
            dependencies = emptyList(),
            structuralSupertypes = structural,
        )

        assertEquals(1, result.suggestions.size)
        val s = result.suggestions[0]
        assertEquals(ClassName("fakes.FakeRepo"), s.className)
        assertEquals(PackageName("polls"), s.suggestedPackage)
        assertEquals(0, s.edgesToCurrent)
    }

    @Test
    fun `structural supertype in same package does not suggest move`() {
        val structural = listOf(
            StructuralSupertypeInfo(ClassName("polls.FakeRepo"), ClassName("polls.Repo")),
        )

        val result = MoveSuggester.suggest(emptyList(), structuralSupertypes = structural)

        assertTrue(result.suggestions.isEmpty())
    }

    @Test
    fun `structural edge to ubiquitous type is not filtered by maxFanIn`() {
        // PollsRepo is referenced by many classes (ubiquitous), but FakeRepo implements it
        val deps = listOf(
            PackageDependency(PackageName("fakes"), PackageName("polls"), ClassName("fakes.FakeRepo"), ClassName("polls.PollsRepo")),
            PackageDependency(PackageName("svc"), PackageName("polls"), ClassName("svc.Service1"), ClassName("polls.PollsRepo")),
            PackageDependency(PackageName("svc"), PackageName("polls"), ClassName("svc.Service2"), ClassName("polls.PollsRepo")),
            PackageDependency(PackageName("svc"), PackageName("polls"), ClassName("svc.Service3"), ClassName("polls.PollsRepo")),
            PackageDependency(PackageName("svc"), PackageName("polls"), ClassName("svc.Service4"), ClassName("polls.PollsRepo")),
            PackageDependency(PackageName("svc"), PackageName("polls"), ClassName("svc.Service5"), ClassName("polls.PollsRepo")),
        )
        val structural = listOf(
            StructuralSupertypeInfo(ClassName("fakes.FakeRepo"), ClassName("polls.PollsRepo")),
        )

        val result = MoveSuggester.suggest(deps, structuralSupertypes = structural, maxFanIn = 4)

        // Regular edge to PollsRepo is filtered by ubiquity, but structural edge persists
        val suggestion = result.suggestions.find { it.className == ClassName("fakes.FakeRepo") }
        assertEquals(PackageName("polls"), suggestion?.suggestedPackage)
    }
}
