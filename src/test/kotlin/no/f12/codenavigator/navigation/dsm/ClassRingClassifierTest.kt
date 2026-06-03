package no.f12.codenavigator.navigation.dsm

import no.f12.codenavigator.navigation.types.ClassName
import no.f12.codenavigator.navigation.types.PackageName
import kotlin.test.Test
import kotlin.test.assertEquals

class ClassRingClassifierTest {

    // [TEST] Class with no external framework imports and no project deps is ring 0 (domain)
    // [TEST] Class importing framework types (e.g. io.ktor) is adapter ring
    // [TEST] Class with only project-internal deps on domain classes is ring 1 (application)
    // [TEST] Class with project-internal deps on application classes is ring 2
    // [TEST] Adapter classes are always outermost ring regardless of project deps
    // [TEST] Interface with no framework deps is classified as domain/application based on its own deps
    // [TEST] Multiple classes in same package can have different rings

    @Test
    fun `class with no framework imports and no project deps is ring 0`() {
        val classDeps = mapOf(
            ClassName("com.app.domain.Order") to ClassDependencies(
                projectDeps = emptySet(),
                externalDeps = setOf(ClassName("kotlin.String"), ClassName("java.util.UUID")),
            )
        )

        val result = ClassRingClassifier.classify(classDeps)

        assertEquals(0, result[ClassName("com.app.domain.Order")])
    }

    @Test
    fun `class importing framework types is at least ring 1`() {
        val classDeps = mapOf(
            ClassName("com.app.web.Controller") to ClassDependencies(
                projectDeps = emptySet(),
                externalDeps = setOf(ClassName("io.ktor.server.routing.Route")),
            )
        )

        val result = ClassRingClassifier.classify(classDeps)

        assertEquals(1, result[ClassName("com.app.web.Controller")])
    }

    @Test
    fun `class depending only on domain classes is ring 1`() {
        val classDeps = mapOf(
            ClassName("com.app.domain.Order") to ClassDependencies(
                projectDeps = emptySet(),
                externalDeps = emptySet(),
            ),
            ClassName("com.app.service.OrderService") to ClassDependencies(
                projectDeps = setOf(ClassName("com.app.domain.Order")),
                externalDeps = emptySet(),
            ),
        )

        val result = ClassRingClassifier.classify(classDeps)

        assertEquals(0, result[ClassName("com.app.domain.Order")])
        assertEquals(1, result[ClassName("com.app.service.OrderService")])
    }

    @Test
    fun `adapter depending on domain class is at outermost position`() {
        val classDeps = mapOf(
            ClassName("com.app.domain.Order") to ClassDependencies(
                projectDeps = emptySet(),
                externalDeps = emptySet(),
            ),
            ClassName("com.app.service.OrderService") to ClassDependencies(
                projectDeps = setOf(ClassName("com.app.domain.Order")),
                externalDeps = emptySet(),
            ),
            ClassName("com.app.web.OrderController") to ClassDependencies(
                projectDeps = setOf(ClassName("com.app.service.OrderService")),
                externalDeps = setOf(ClassName("io.ktor.server.routing.Route")),
            ),
        )

        val result = ClassRingClassifier.classify(classDeps)

        assertEquals(0, result[ClassName("com.app.domain.Order")])
        assertEquals(1, result[ClassName("com.app.service.OrderService")])
        assertEquals(2, result[ClassName("com.app.web.OrderController")])
    }

    @Test
    fun `multiple classes in same package get different rings`() {
        val classDeps = mapOf(
            ClassName("com.app.auth.Device") to ClassDependencies(
                projectDeps = emptySet(),
                externalDeps = emptySet(),
            ),
            ClassName("com.app.auth.DeviceRepository") to ClassDependencies(
                projectDeps = setOf(ClassName("com.app.auth.Device")),
                externalDeps = emptySet(),
            ),
            ClassName("com.app.auth.DeviceRepositoryImpl") to ClassDependencies(
                projectDeps = setOf(ClassName("com.app.auth.Device"), ClassName("com.app.auth.DeviceRepository")),
                externalDeps = setOf(ClassName("org.jetbrains.exposed.sql.Table")),
            ),
        )

        val result = ClassRingClassifier.classify(classDeps)

        assertEquals(0, result[ClassName("com.app.auth.Device")])
        assertEquals(1, result[ClassName("com.app.auth.DeviceRepository")])
        assertEquals(2, result[ClassName("com.app.auth.DeviceRepositoryImpl")])
    }

    @Test
    fun `cyclic dependency between classes collapses to same ring`() {
        val classDeps = mapOf(
            ClassName("com.app.A") to ClassDependencies(
                projectDeps = setOf(ClassName("com.app.B")),
                externalDeps = emptySet(),
            ),
            ClassName("com.app.B") to ClassDependencies(
                projectDeps = setOf(ClassName("com.app.A")),
                externalDeps = emptySet(),
            ),
        )

        val result = ClassRingClassifier.classify(classDeps)

        // Both in a cycle with no external deps — should be same ring (ring 0)
        assertEquals(result[ClassName("com.app.A")], result[ClassName("com.app.B")])
        assertEquals(0, result[ClassName("com.app.A")])
    }
}
