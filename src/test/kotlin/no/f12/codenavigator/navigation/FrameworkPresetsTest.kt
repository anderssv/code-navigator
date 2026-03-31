package no.f12.codenavigator.navigation

import no.f12.codenavigator.navigation.annotation.FrameworkPresets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FrameworkPresetsTest {

    @Test
    fun `spring preset includes Controller and Component`() {
        val annotations = FrameworkPresets.resolve("spring")

        assertTrue(annotations.contains(AnnotationName("org.springframework.stereotype.Controller")))
        assertTrue(annotations.contains(AnnotationName("org.springframework.stereotype.Component")))
        assertTrue(annotations.contains(AnnotationName("org.springframework.web.bind.annotation.RestController")))
        assertTrue(annotations.contains(AnnotationName("org.springframework.stereotype.Service")))
        assertTrue(annotations.contains(AnnotationName("org.springframework.stereotype.Repository")))
        assertTrue(annotations.contains(AnnotationName("org.springframework.context.annotation.Configuration")))
        assertTrue(annotations.contains(AnnotationName("org.springframework.context.annotation.Bean")))
    }

    @Test
    fun `unknown framework returns empty set`() {
        val annotations = FrameworkPresets.resolve("unknown-framework")

        assertTrue(annotations.isEmpty())
    }

    @Test
    fun `resolving multiple frameworks merges their annotations`() {
        val annotations = FrameworkPresets.resolveAll(listOf("spring"))

        assertTrue(annotations.contains(AnnotationName("org.springframework.stereotype.Controller")))
        assertTrue(annotations.contains(AnnotationName("org.springframework.context.annotation.Bean")))
    }

    @Test
    fun `resolving multiple frameworks with unknown returns only known`() {
        val annotations = FrameworkPresets.resolveAll(listOf("spring", "unknown"))

        assertTrue(annotations.contains(AnnotationName("org.springframework.stereotype.Controller")))
        assertTrue(annotations.isNotEmpty())
    }

    @Test
    fun `framework names are case-insensitive`() {
        val upper = FrameworkPresets.resolve("Spring")
        val lower = FrameworkPresets.resolve("spring")
        val mixed = FrameworkPresets.resolve("SPRING")

        assertEquals(lower, upper)
        assertEquals(lower, mixed)
    }

    @Test
    fun `available presets includes spring`() {
        val presets = FrameworkPresets.availablePresets()

        assertTrue(presets.contains("spring"))
    }

    @Test
    fun `spring preset includes JPA annotations`() {
        val annotations = FrameworkPresets.resolve("spring")

        assertTrue(annotations.contains(AnnotationName("jakarta.persistence.Entity")))
        assertTrue(annotations.contains(AnnotationName("jakarta.persistence.MappedSuperclass")))
    }

    @Test
    fun `spring preset includes SpringBootApplication`() {
        val annotations = FrameworkPresets.resolve("spring")

        assertTrue(annotations.contains(AnnotationName("org.springframework.boot.autoconfigure.SpringBootApplication")))
    }

    @Test
    fun `jpa preset includes Entity`() {
        val annotations = FrameworkPresets.resolve("jpa")

        assertTrue(annotations.contains(AnnotationName("jakarta.persistence.Entity")))
        assertTrue(annotations.contains(AnnotationName("jakarta.persistence.MappedSuperclass")))
        assertTrue(annotations.contains(AnnotationName("jakarta.persistence.Table")))
    }

    @Test
    fun `jackson preset includes JsonCreator`() {
        val annotations = FrameworkPresets.resolve("jackson")

        assertTrue(annotations.contains(AnnotationName("com.fasterxml.jackson.annotation.JsonCreator")))
    }

    @Test
    fun `resolving multiple distinct frameworks merges all annotations`() {
        val annotations = FrameworkPresets.resolveAll(listOf("jpa", "jackson"))

        assertTrue(annotations.contains(AnnotationName("jakarta.persistence.Entity")))
        assertTrue(annotations.contains(AnnotationName("com.fasterxml.jackson.annotation.JsonCreator")))
    }

    @Test
    fun `resolveAll with empty list returns empty set`() {
        val annotations = FrameworkPresets.resolveAll(emptyList())

        assertTrue(annotations.isEmpty())
    }

    @Test
    fun `frameworkOf returns spring for a Spring annotation`() {
        val framework = FrameworkPresets.frameworkOf(AnnotationName("org.springframework.stereotype.Controller"))

        assertEquals("spring", framework)
    }

    @Test
    fun `frameworkOf returns jpa for a JPA annotation not spring`() {
        val framework = FrameworkPresets.frameworkOf(AnnotationName("jakarta.persistence.Entity"))

        assertEquals("jpa", framework)
    }

    @Test
    fun `frameworkOf returns jackson for a Jackson annotation`() {
        val framework = FrameworkPresets.frameworkOf(AnnotationName("com.fasterxml.jackson.annotation.JsonCreator"))

        assertEquals("jackson", framework)
    }

    @Test
    fun `frameworkOf returns null for unknown annotation`() {
        val framework = FrameworkPresets.frameworkOf(AnnotationName("com.example.CustomAnnotation"))

        assertEquals(null, framework)
    }

    @Test
    fun `junit preset includes Test and BeforeEach`() {
        val annotations = FrameworkPresets.resolve("junit")

        assertTrue(annotations.contains(AnnotationName("org.junit.jupiter.api.Test")))
        assertTrue(annotations.contains(AnnotationName("org.junit.jupiter.api.BeforeEach")))
        assertTrue(annotations.contains(AnnotationName("org.junit.jupiter.api.AfterEach")))
        assertTrue(annotations.contains(AnnotationName("org.junit.jupiter.params.ParameterizedTest")))
        assertTrue(annotations.contains(AnnotationName("org.junit.jupiter.api.Disabled")))
    }

    @Test
    fun `frameworkOf returns junit for Test annotation`() {
        val framework = FrameworkPresets.frameworkOf(AnnotationName("org.junit.jupiter.api.Test"))

        assertEquals("junit", framework)
    }

    @Test
    fun `jakarta preset includes PostConstruct and PreDestroy`() {
        val annotations = FrameworkPresets.resolve("jakarta")

        assertTrue(annotations.contains(AnnotationName("jakarta.annotation.PostConstruct")))
        assertTrue(annotations.contains(AnnotationName("jakarta.annotation.PreDestroy")))
    }

    @Test
    fun `jakarta preset includes Inject and Named`() {
        val annotations = FrameworkPresets.resolve("jakarta")

        assertTrue(annotations.contains(AnnotationName("jakarta.inject.Inject")))
        assertTrue(annotations.contains(AnnotationName("jakarta.inject.Named")))
        assertTrue(annotations.contains(AnnotationName("jakarta.inject.Singleton")))
    }

    @Test
    fun `jakarta preset is available in availablePresets`() {
        val presets = FrameworkPresets.availablePresets()

        assertTrue(presets.contains("jakarta"))
    }

    @Test
    fun `frameworkOf returns jakarta for PostConstruct`() {
        val framework = FrameworkPresets.frameworkOf(AnnotationName("jakarta.annotation.PostConstruct"))

        assertEquals("jakarta", framework)
    }

    @Test
    fun `spring preset includes jakarta annotations`() {
        val annotations = FrameworkPresets.resolve("spring")

        assertTrue(annotations.contains(AnnotationName("jakarta.annotation.PostConstruct")))
        assertTrue(annotations.contains(AnnotationName("jakarta.annotation.PreDestroy")))
        assertTrue(annotations.contains(AnnotationName("jakarta.inject.Inject")))
    }

    @Test
    fun `validation preset includes jakarta validation Valid`() {
        val annotations = FrameworkPresets.resolve("validation")

        assertTrue(annotations.contains(AnnotationName("jakarta.validation.Valid")))
    }

    @Test
    fun `validation preset includes jakarta validation constraints`() {
        val annotations = FrameworkPresets.resolve("validation")

        assertTrue(annotations.contains(AnnotationName("jakarta.validation.constraints.NotBlank")))
        assertTrue(annotations.contains(AnnotationName("jakarta.validation.constraints.NotNull")))
        assertTrue(annotations.contains(AnnotationName("jakarta.validation.constraints.Size")))
        assertTrue(annotations.contains(AnnotationName("jakarta.validation.constraints.Min")))
        assertTrue(annotations.contains(AnnotationName("jakarta.validation.constraints.Max")))
        assertTrue(annotations.contains(AnnotationName("jakarta.validation.constraints.Pattern")))
        assertTrue(annotations.contains(AnnotationName("jakarta.validation.constraints.Email")))
    }

    @Test
    fun `validation preset includes hibernate validator annotations`() {
        val annotations = FrameworkPresets.resolve("validation")

        assertTrue(annotations.contains(AnnotationName("org.hibernate.validator.constraints.Length")))
        assertTrue(annotations.contains(AnnotationName("org.hibernate.validator.constraints.Range")))
        assertTrue(annotations.contains(AnnotationName("org.hibernate.validator.constraints.URL")))
    }

    @Test
    fun `validation preset is available in availablePresets`() {
        val presets = FrameworkPresets.availablePresets()

        assertTrue(presets.contains("validation"))
    }

    @Test
    fun `frameworkOf returns validation for NotBlank`() {
        val framework = FrameworkPresets.frameworkOf(AnnotationName("jakarta.validation.constraints.NotBlank"))

        assertEquals("validation", framework)
    }

    @Test
    fun `spring preset includes validation annotations`() {
        val annotations = FrameworkPresets.resolve("spring")

        assertTrue(annotations.contains(AnnotationName("jakarta.validation.Valid")))
        assertTrue(annotations.contains(AnnotationName("jakarta.validation.constraints.NotBlank")))
        assertTrue(annotations.contains(AnnotationName("jakarta.validation.constraints.NotNull")))
    }

    // --- Entry-point vs modifier distinction ---

    @Test
    fun `Transactional is a modifier not an entry-point in spring`() {
        val entryPoints = FrameworkPresets.resolveEntryPoints("spring")
        val modifiers = FrameworkPresets.resolveModifiers("spring")

        val transactional = AnnotationName("org.springframework.transaction.annotation.Transactional")
        assertTrue(modifiers.contains(transactional), "Transactional should be a modifier")
        assertFalse(entryPoints.contains(transactional), "Transactional should NOT be an entry-point")
    }

    @Test
    fun `GetMapping is an entry-point not a modifier in spring`() {
        val entryPoints = FrameworkPresets.resolveEntryPoints("spring")
        val modifiers = FrameworkPresets.resolveModifiers("spring")

        val getMapping = AnnotationName("org.springframework.web.bind.annotation.GetMapping")
        assertTrue(entryPoints.contains(getMapping), "GetMapping should be an entry-point")
        assertFalse(modifiers.contains(getMapping), "GetMapping should NOT be a modifier")
    }

    @Test
    fun `Cacheable and Async are modifiers in spring`() {
        val modifiers = FrameworkPresets.resolveModifiers("spring")

        assertTrue(modifiers.contains(AnnotationName("org.springframework.cache.annotation.Cacheable")))
        assertTrue(modifiers.contains(AnnotationName("org.springframework.cache.annotation.CacheEvict")))
        assertTrue(modifiers.contains(AnnotationName("org.springframework.cache.annotation.CachePut")))
        assertTrue(modifiers.contains(AnnotationName("org.springframework.scheduling.annotation.Async")))
    }

    @Test
    fun `resolve returns union of entry-points and modifiers`() {
        val all = FrameworkPresets.resolve("spring")
        val entryPoints = FrameworkPresets.resolveEntryPoints("spring")
        val modifiers = FrameworkPresets.resolveModifiers("spring")

        assertTrue(all.containsAll(entryPoints))
        assertTrue(all.containsAll(modifiers))
        assertTrue(all.contains(AnnotationName("org.springframework.transaction.annotation.Transactional")))
        assertTrue(all.contains(AnnotationName("org.springframework.web.bind.annotation.GetMapping")))
    }

    // --- JAX-RS preset ---

    @Test
    fun `jaxrs preset contains HTTP method entry-points`() {
        val entryPoints = FrameworkPresets.resolveEntryPoints("jaxrs")

        assertTrue(entryPoints.contains(AnnotationName("jakarta.ws.rs.GET")))
        assertTrue(entryPoints.contains(AnnotationName("jakarta.ws.rs.POST")))
        assertTrue(entryPoints.contains(AnnotationName("jakarta.ws.rs.PUT")))
        assertTrue(entryPoints.contains(AnnotationName("jakarta.ws.rs.DELETE")))
        assertTrue(entryPoints.contains(AnnotationName("jakarta.ws.rs.PATCH")))
        assertTrue(entryPoints.contains(AnnotationName("jakarta.ws.rs.Path")))
        assertTrue(entryPoints.contains(AnnotationName("jakarta.ws.rs.ext.Provider")))
    }

    @Test
    fun `jaxrs preset is in availablePresets`() {
        assertTrue(FrameworkPresets.availablePresets().contains("jaxrs"))
    }

    // --- CDI preset ---

    @Test
    fun `cdi preset contains scope and lifecycle entry-points`() {
        val entryPoints = FrameworkPresets.resolveEntryPoints("cdi")

        assertTrue(entryPoints.contains(AnnotationName("jakarta.enterprise.context.ApplicationScoped")))
        assertTrue(entryPoints.contains(AnnotationName("jakarta.enterprise.context.RequestScoped")))
        assertTrue(entryPoints.contains(AnnotationName("jakarta.enterprise.inject.Produces")))
        assertTrue(entryPoints.contains(AnnotationName("jakarta.enterprise.event.Observes")))
    }

    @Test
    fun `cdi preset contains interceptor entry-points`() {
        val entryPoints = FrameworkPresets.resolveEntryPoints("cdi")

        assertTrue(entryPoints.contains(AnnotationName("jakarta.interceptor.Interceptor")))
        assertTrue(entryPoints.contains(AnnotationName("jakarta.interceptor.AroundInvoke")))
    }

    // --- MicroProfile preset ---

    @Test
    fun `microprofile preset contains health check entry-points`() {
        val entryPoints = FrameworkPresets.resolveEntryPoints("microprofile")

        assertTrue(entryPoints.contains(AnnotationName("org.eclipse.microprofile.health.Liveness")))
        assertTrue(entryPoints.contains(AnnotationName("org.eclipse.microprofile.health.Readiness")))
    }

    @Test
    fun `microprofile fault tolerance annotations are modifiers`() {
        val entryPoints = FrameworkPresets.resolveEntryPoints("microprofile")
        val modifiers = FrameworkPresets.resolveModifiers("microprofile")

        val circuitBreaker = AnnotationName("org.eclipse.microprofile.faulttolerance.CircuitBreaker")
        val retry = AnnotationName("org.eclipse.microprofile.faulttolerance.Retry")
        val timeout = AnnotationName("org.eclipse.microprofile.faulttolerance.Timeout")

        assertTrue(modifiers.contains(circuitBreaker))
        assertTrue(modifiers.contains(retry))
        assertTrue(modifiers.contains(timeout))
        assertFalse(entryPoints.contains(circuitBreaker))
        assertFalse(entryPoints.contains(retry))
        assertFalse(entryPoints.contains(timeout))
    }

    // --- Quarkus composite preset ---

    @Test
    fun `quarkus preset includes jaxrs cdi microprofile jpa jakarta validation`() {
        val all = FrameworkPresets.resolve("quarkus")

        assertTrue(all.contains(AnnotationName("jakarta.ws.rs.GET")), "JAX-RS")
        assertTrue(all.contains(AnnotationName("jakarta.enterprise.context.ApplicationScoped")), "CDI")
        assertTrue(all.contains(AnnotationName("org.eclipse.microprofile.health.Liveness")), "MicroProfile")
        assertTrue(all.contains(AnnotationName("jakarta.persistence.Entity")), "JPA")
        assertTrue(all.contains(AnnotationName("jakarta.inject.Inject")), "Jakarta")
        assertTrue(all.contains(AnnotationName("jakarta.validation.Valid")), "Validation")
    }

    @Test
    fun `quarkus preset contains Quarkus-specific entry-points`() {
        val entryPoints = FrameworkPresets.resolveEntryPoints("quarkus")

        assertTrue(entryPoints.contains(AnnotationName("io.quarkus.scheduler.Scheduled")))
    }

    @Test
    fun `quarkus preset is in availablePresets`() {
        assertTrue(FrameworkPresets.availablePresets().contains("quarkus"))
    }

    @Test
    fun `resolveAllEntryPoints merges entry-points from multiple presets`() {
        val entryPoints = FrameworkPresets.resolveAllEntryPoints(listOf("jaxrs", "cdi"))

        assertTrue(entryPoints.contains(AnnotationName("jakarta.ws.rs.GET")))
        assertTrue(entryPoints.contains(AnnotationName("jakarta.enterprise.context.ApplicationScoped")))
    }

    @Test
    fun `resolveAllModifiers merges modifiers from multiple presets`() {
        val modifiers = FrameworkPresets.resolveAllModifiers(listOf("spring", "microprofile"))

        assertTrue(modifiers.contains(AnnotationName("org.springframework.transaction.annotation.Transactional")))
        assertTrue(modifiers.contains(AnnotationName("org.eclipse.microprofile.faulttolerance.CircuitBreaker")))
    }

    // --- resolveAllEntryPointsExcept / resolveAllModifiersExcept ---

    @Test
    fun `resolveAllEntryPointsExcept with empty exclusion returns all entry-points`() {
        val all = FrameworkPresets.resolveAllEntryPointsExcept(emptyList())

        assertTrue(all.contains(AnnotationName("org.springframework.stereotype.Controller")))
        assertTrue(all.contains(AnnotationName("jakarta.ws.rs.GET")))
        assertTrue(all.contains(AnnotationName("io.quarkus.scheduler.Scheduled")))
        assertTrue(all.contains(AnnotationName("org.junit.jupiter.api.Test")))
    }

    @Test
    fun `resolveAllEntryPointsExcept excludes named preset`() {
        val result = FrameworkPresets.resolveAllEntryPointsExcept(listOf("spring"))

        assertFalse(result.contains(AnnotationName("org.springframework.stereotype.Controller")))
        assertTrue(result.contains(AnnotationName("jakarta.ws.rs.GET")))
    }

    @Test
    fun `resolveAllModifiersExcept with empty exclusion returns all modifiers`() {
        val all = FrameworkPresets.resolveAllModifiersExcept(emptyList())

        assertTrue(all.contains(AnnotationName("org.springframework.transaction.annotation.Transactional")))
        assertTrue(all.contains(AnnotationName("org.eclipse.microprofile.faulttolerance.CircuitBreaker")))
    }

    @Test
    fun `resolveAllModifiersExcept excludes named preset`() {
        val result = FrameworkPresets.resolveAllModifiersExcept(listOf("spring"))

        assertFalse(result.contains(AnnotationName("org.springframework.transaction.annotation.Transactional")))
        assertTrue(result.contains(AnnotationName("org.eclipse.microprofile.faulttolerance.CircuitBreaker")))
    }

    @Test
    fun `resolveAllEntryPointsExcept ALL returns empty set`() {
        val result = FrameworkPresets.resolveAllEntryPointsExcept(listOf("ALL"))

        assertTrue(result.isEmpty())
    }

    @Test
    fun `resolveAllModifiersExcept ALL returns empty set`() {
        val result = FrameworkPresets.resolveAllModifiersExcept(listOf("ALL"))

        assertTrue(result.isEmpty())
    }

    @Test
    fun `resolveAllEntryPointsExcept is case-insensitive`() {
        val result = FrameworkPresets.resolveAllEntryPointsExcept(listOf("Spring"))

        assertFalse(result.contains(AnnotationName("org.springframework.stereotype.Controller")))
    }

    // --- gRPC preset ---

    @Test
    fun `grpc preset is in availablePresets`() {
        assertTrue(FrameworkPresets.availablePresets().contains("grpc"))
    }

    @Test
    fun `grpc preset contains Quarkus GrpcService entry-point`() {
        val entryPoints = FrameworkPresets.resolveEntryPoints("grpc")

        assertTrue(entryPoints.contains(AnnotationName("io.quarkus.grpc.GrpcService")))
        assertTrue(entryPoints.contains(AnnotationName("io.quarkus.grpc.GrpcClient")))
        assertTrue(entryPoints.contains(AnnotationName("io.quarkus.grpc.GlobalInterceptor")))
    }

    @Test
    fun `grpc preset contains Spring gRPC entry-points`() {
        val entryPoints = FrameworkPresets.resolveEntryPoints("grpc")

        assertTrue(entryPoints.contains(AnnotationName("net.devh.boot.grpc.server.service.GrpcService")))
        assertTrue(entryPoints.contains(AnnotationName("net.devh.boot.grpc.client.inject.GrpcClient")))
    }

    @Test
    fun `grpc preset contains GrpcGenerated entry-point`() {
        val entryPoints = FrameworkPresets.resolveEntryPoints("grpc")

        assertTrue(entryPoints.contains(AnnotationName("io.grpc.stub.annotations.GrpcGenerated")))
    }

    @Test
    fun `grpc preset contains SmallRye Blocking modifier`() {
        val entryPoints = FrameworkPresets.resolveEntryPoints("grpc")
        val modifiers = FrameworkPresets.resolveModifiers("grpc")

        val blocking = AnnotationName("io.smallrye.common.annotation.Blocking")
        assertTrue(modifiers.contains(blocking))
        assertFalse(entryPoints.contains(blocking))
    }

    @Test
    fun `grpc preset contains RegisterInterceptor modifier`() {
        val modifiers = FrameworkPresets.resolveModifiers("grpc")

        assertTrue(modifiers.contains(AnnotationName("io.quarkus.grpc.RegisterInterceptor")))
    }

    @Test
    fun `frameworkOf returns grpc for GrpcService annotation`() {
        val framework = FrameworkPresets.frameworkOf(AnnotationName("io.quarkus.grpc.GrpcService"))

        assertEquals("grpc", framework)
    }

    @Test
    fun `quarkus composite preset includes grpc entry-points`() {
        val entryPoints = FrameworkPresets.resolveEntryPoints("quarkus")

        assertTrue(entryPoints.contains(AnnotationName("io.quarkus.grpc.GrpcService")))
        assertTrue(entryPoints.contains(AnnotationName("io.quarkus.grpc.GrpcClient")))
    }

    @Test
    fun `spring composite preset includes Spring gRPC entry-points`() {
        val entryPoints = FrameworkPresets.resolveEntryPoints("spring")

        assertTrue(entryPoints.contains(AnnotationName("net.devh.boot.grpc.server.service.GrpcService")))
        assertTrue(entryPoints.contains(AnnotationName("net.devh.boot.grpc.client.inject.GrpcClient")))
    }

    @Test
    fun `spring preset includes Spring Data supertype entry points`() {
        val supertypes = FrameworkPresets.resolveSupertypeEntryPoints("spring")

        assertTrue(supertypes.contains(ClassName("org.springframework.data.repository.CrudRepository")))
        assertTrue(supertypes.contains(ClassName("org.springframework.data.jpa.repository.JpaRepository")))
        assertTrue(supertypes.contains(ClassName("org.springframework.data.repository.PagingAndSortingRepository")))
        assertTrue(supertypes.contains(ClassName("org.springframework.data.repository.reactive.ReactiveCrudRepository")))
        assertTrue(supertypes.contains(ClassName("org.springframework.data.mongodb.repository.MongoRepository")))
    }

    @Test
    fun `resolveAllSupertypeEntryPointsExcept returns empty when ALL excluded`() {
        val supertypes = FrameworkPresets.resolveAllSupertypeEntryPointsExcept(listOf("ALL"))

        assertTrue(supertypes.isEmpty())
    }

    @Test
    fun `resolveAllSupertypeEntryPointsExcept excludes specific preset`() {
        val withSpring = FrameworkPresets.resolveAllSupertypeEntryPointsExcept(emptyList())
        val withoutSpring = FrameworkPresets.resolveAllSupertypeEntryPointsExcept(listOf("spring"))

        assertTrue(withSpring.contains(ClassName("org.springframework.data.jpa.repository.JpaRepository")))
        assertFalse(withoutSpring.contains(ClassName("org.springframework.data.jpa.repository.JpaRepository")))
    }

    @Test
    fun `preset without supertype entry points returns empty set`() {
        val supertypes = FrameworkPresets.resolveSupertypeEntryPoints("jackson")

        assertTrue(supertypes.isEmpty())
    }

    @Test
    fun `quarkus preset includes Panache supertype entry points`() {
        val supertypes = FrameworkPresets.resolveSupertypeEntryPoints("quarkus")

        assertTrue(supertypes.contains(ClassName("io.quarkus.hibernate.orm.panache.PanacheRepository")))
        assertTrue(supertypes.contains(ClassName("io.quarkus.hibernate.orm.panache.PanacheRepositoryBase")))
    }

    // --- Ktor preset ---

    @Test
    fun `ktor preset is in availablePresets`() {
        assertTrue(FrameworkPresets.availablePresets().contains("ktor"))
    }

    @Test
    fun `ktor preset includes supertype entry points for AuthenticationProvider and Plugin`() {
        val supertypes = FrameworkPresets.resolveSupertypeEntryPoints("ktor")

        assertTrue(supertypes.contains(ClassName("io.ktor.server.auth.AuthenticationProvider")))
        assertTrue(supertypes.contains(ClassName("io.ktor.server.application.BaseApplicationPlugin")))
        assertTrue(supertypes.contains(ClassName("io.ktor.server.application.BaseRouteScopedPlugin")))
    }

    @Test
    fun `ktor preset includes supertype entry points for ContentConverter and Template`() {
        val supertypes = FrameworkPresets.resolveSupertypeEntryPoints("ktor")

        assertTrue(supertypes.contains(ClassName("io.ktor.serialization.ContentConverter")))
        assertTrue(supertypes.contains(ClassName("io.ktor.server.html.Template")))
    }

    @Test
    fun `ktor preset has no annotation entry points`() {
        val entryPoints = FrameworkPresets.resolveEntryPoints("ktor")

        assertTrue(entryPoints.isEmpty(), "Ktor uses DSL not annotations for entry points")
    }

}
