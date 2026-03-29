package no.f12.codenavigator.navigation.annotation

import no.f12.codenavigator.navigation.AnnotationName

object FrameworkPresets {

    private val SPRING = setOf(
        "org.springframework.stereotype.Controller",
        "org.springframework.web.bind.annotation.RestController",
        "org.springframework.stereotype.Service",
        "org.springframework.stereotype.Component",
        "org.springframework.stereotype.Repository",
        "org.springframework.context.annotation.Configuration",
        "org.springframework.context.annotation.Bean",
        "org.springframework.scheduling.annotation.Scheduled",
        "org.springframework.context.event.EventListener",
        "org.springframework.web.bind.annotation.ExceptionHandler",
        "org.springframework.web.bind.annotation.ControllerAdvice",
        "org.springframework.boot.actuate.endpoint.annotation.Endpoint",
        "org.springframework.boot.autoconfigure.SpringBootApplication",
        "org.springframework.boot.autoconfigure.EnableAutoConfiguration",
        "org.springframework.context.annotation.ComponentScan",
        "org.springframework.web.bind.annotation.GetMapping",
        "org.springframework.web.bind.annotation.PostMapping",
        "org.springframework.web.bind.annotation.PutMapping",
        "org.springframework.web.bind.annotation.DeleteMapping",
        "org.springframework.web.bind.annotation.PatchMapping",
        "org.springframework.web.bind.annotation.RequestMapping",
        "org.springframework.web.bind.annotation.ModelAttribute",
        "org.springframework.web.bind.annotation.InitBinder",
        "org.springframework.web.bind.annotation.ResponseBody",
        "org.springframework.web.bind.annotation.RequestBody",
        "org.springframework.web.bind.annotation.PathVariable",
        "org.springframework.web.bind.annotation.RequestParam",
        "org.springframework.beans.factory.annotation.Autowired",
        "org.springframework.beans.factory.annotation.Value",
        "org.springframework.beans.factory.annotation.Qualifier",
        "org.springframework.context.annotation.Primary",
        "org.springframework.context.annotation.Lazy",
        "org.springframework.context.annotation.Scope",
        "org.springframework.context.annotation.Profile",
        "org.springframework.context.annotation.Conditional",
        "org.springframework.context.annotation.Import",
        "org.springframework.context.annotation.ImportResource",
        "org.springframework.context.annotation.PropertySource",
        "org.springframework.cache.annotation.EnableCaching",
    ).map { AnnotationName(it) }.toSet()

    private val JPA = setOf(
        "jakarta.persistence.Entity",
        "jakarta.persistence.Table",
        "jakarta.persistence.MappedSuperclass",
        "jakarta.persistence.Embeddable",
        "jakarta.persistence.Converter",
        "jakarta.persistence.EntityListeners",
        "jakarta.persistence.Id",
        "jakarta.persistence.GeneratedValue",
        "jakarta.persistence.Column",
        "jakarta.persistence.JoinColumn",
        "jakarta.persistence.OneToMany",
        "jakarta.persistence.ManyToOne",
        "jakarta.persistence.OneToOne",
        "jakarta.persistence.ManyToMany",
        "jakarta.persistence.Transient",
        "jakarta.persistence.Enumerated",
        "jakarta.persistence.Temporal",
        "jakarta.persistence.Lob",
        "jakarta.persistence.NamedQuery",
        "jakarta.persistence.NamedQueries",
    ).map { AnnotationName(it) }.toSet()

    private val JACKSON = setOf(
        "com.fasterxml.jackson.annotation.JsonCreator",
        "com.fasterxml.jackson.annotation.JsonProperty",
        "com.fasterxml.jackson.annotation.JsonIgnore",
        "com.fasterxml.jackson.annotation.JsonIgnoreProperties",
        "com.fasterxml.jackson.annotation.JsonFormat",
        "com.fasterxml.jackson.annotation.JsonValue",
        "com.fasterxml.jackson.annotation.JsonTypeInfo",
        "com.fasterxml.jackson.annotation.JsonSubTypes",
        "com.fasterxml.jackson.annotation.JsonTypeName",
        "com.fasterxml.jackson.annotation.JsonInclude",
        "com.fasterxml.jackson.annotation.JsonAlias",
        "com.fasterxml.jackson.annotation.JsonUnwrapped",
        "com.fasterxml.jackson.annotation.JsonRootName",
        "com.fasterxml.jackson.databind.annotation.JsonDeserialize",
        "com.fasterxml.jackson.databind.annotation.JsonSerialize",
    ).map { AnnotationName(it) }.toSet()

    private val JAKARTA = setOf(
        "jakarta.annotation.PostConstruct",
        "jakarta.annotation.PreDestroy",
        "jakarta.inject.Inject",
        "jakarta.inject.Named",
        "jakarta.inject.Singleton",
        "jakarta.inject.Qualifier",
    ).map { AnnotationName(it) }.toSet()

    private val VALIDATION = setOf(
        "jakarta.validation.Valid",
        "jakarta.validation.constraints.NotNull",
        "jakarta.validation.constraints.NotBlank",
        "jakarta.validation.constraints.NotEmpty",
        "jakarta.validation.constraints.Size",
        "jakarta.validation.constraints.Min",
        "jakarta.validation.constraints.Max",
        "jakarta.validation.constraints.Pattern",
        "jakarta.validation.constraints.Email",
        "jakarta.validation.constraints.Positive",
        "jakarta.validation.constraints.PositiveOrZero",
        "jakarta.validation.constraints.Negative",
        "jakarta.validation.constraints.NegativeOrZero",
        "jakarta.validation.constraints.Past",
        "jakarta.validation.constraints.PastOrPresent",
        "jakarta.validation.constraints.Future",
        "jakarta.validation.constraints.FutureOrPresent",
        "jakarta.validation.constraints.Digits",
        "jakarta.validation.constraints.DecimalMin",
        "jakarta.validation.constraints.DecimalMax",
        "jakarta.validation.constraints.AssertTrue",
        "jakarta.validation.constraints.AssertFalse",
        "jakarta.validation.constraints.Null",
        "org.hibernate.validator.constraints.Length",
        "org.hibernate.validator.constraints.Range",
        "org.hibernate.validator.constraints.URL",
        "org.hibernate.validator.constraints.CreditCardNumber",
    ).map { AnnotationName(it) }.toSet()

    private val JUNIT = setOf(
        "org.junit.jupiter.api.Test",
        "org.junit.jupiter.api.BeforeEach",
        "org.junit.jupiter.api.AfterEach",
        "org.junit.jupiter.api.BeforeAll",
        "org.junit.jupiter.api.AfterAll",
        "org.junit.jupiter.params.ParameterizedTest",
        "org.junit.jupiter.api.RepeatedTest",
        "org.junit.jupiter.api.TestFactory",
        "org.junit.jupiter.api.Disabled",
        "org.junit.jupiter.api.extension.ExtendWith",
        "org.junit.jupiter.api.Tag",
        "org.junit.jupiter.api.Nested",
        "org.junit.jupiter.api.DisplayName",
    ).map { AnnotationName(it) }.toSet()

    private val SPRING_MODIFIERS = setOf(
        "org.springframework.transaction.annotation.Transactional",
        "org.springframework.cache.annotation.Cacheable",
        "org.springframework.cache.annotation.CacheEvict",
        "org.springframework.cache.annotation.CachePut",
        "org.springframework.scheduling.annotation.Async",
    ).map { AnnotationName(it) }.toSet()

    private val JAXRS = setOf(
        "jakarta.ws.rs.GET",
        "jakarta.ws.rs.POST",
        "jakarta.ws.rs.PUT",
        "jakarta.ws.rs.DELETE",
        "jakarta.ws.rs.PATCH",
        "jakarta.ws.rs.HEAD",
        "jakarta.ws.rs.OPTIONS",
        "jakarta.ws.rs.Path",
        "jakarta.ws.rs.Produces",
        "jakarta.ws.rs.Consumes",
        "jakarta.ws.rs.PathParam",
        "jakarta.ws.rs.QueryParam",
        "jakarta.ws.rs.HeaderParam",
        "jakarta.ws.rs.FormParam",
        "jakarta.ws.rs.BeanParam",
        "jakarta.ws.rs.CookieParam",
        "jakarta.ws.rs.DefaultValue",
        "jakarta.ws.rs.ext.Provider",
        "jakarta.ws.rs.NameBinding",
    ).map { AnnotationName(it) }.toSet()

    private val CDI = setOf(
        "jakarta.enterprise.context.ApplicationScoped",
        "jakarta.enterprise.context.RequestScoped",
        "jakarta.enterprise.context.SessionScoped",
        "jakarta.enterprise.context.Dependent",
        "jakarta.enterprise.inject.Produces",
        "jakarta.enterprise.event.Observes",
        "jakarta.enterprise.event.ObservesAsync",
        "jakarta.enterprise.inject.Alternative",
        "jakarta.enterprise.inject.Disposes",
        "jakarta.interceptor.Interceptor",
        "jakarta.interceptor.AroundInvoke",
        "jakarta.interceptor.AroundConstruct",
        "jakarta.decorator.Decorator",
        "jakarta.decorator.Delegate",
    ).map { AnnotationName(it) }.toSet()

    private val MICROPROFILE = setOf(
        "org.eclipse.microprofile.health.Liveness",
        "org.eclipse.microprofile.health.Readiness",
        "org.eclipse.microprofile.health.Startup",
        "org.eclipse.microprofile.rest.client.inject.RegisterRestClient",
        "org.eclipse.microprofile.rest.client.inject.RestClient",
        "org.eclipse.microprofile.config.inject.ConfigProperty",
        "org.eclipse.microprofile.openapi.annotations.Operation",
        "org.eclipse.microprofile.openapi.annotations.responses.APIResponse",
        "org.eclipse.microprofile.openapi.annotations.tags.Tag",
    ).map { AnnotationName(it) }.toSet()

    private val MICROPROFILE_MODIFIERS = setOf(
        "org.eclipse.microprofile.faulttolerance.CircuitBreaker",
        "org.eclipse.microprofile.faulttolerance.Retry",
        "org.eclipse.microprofile.faulttolerance.Timeout",
        "org.eclipse.microprofile.faulttolerance.Bulkhead",
        "org.eclipse.microprofile.faulttolerance.Fallback",
        "org.eclipse.microprofile.faulttolerance.Asynchronous",
    ).map { AnnotationName(it) }.toSet()

    private val QUARKUS = setOf(
        "io.quarkus.scheduler.Scheduled",
        "io.quarkus.vertx.ConsumeEvent",
        "io.quarkus.runtime.Startup",
    ).map { AnnotationName(it) }.toSet()

    private val GRPC = setOf(
        "io.quarkus.grpc.GrpcService",
        "io.quarkus.grpc.GrpcClient",
        "io.quarkus.grpc.GlobalInterceptor",
        "net.devh.boot.grpc.server.service.GrpcService",
        "net.devh.boot.grpc.client.inject.GrpcClient",
        "net.devh.boot.grpc.client.inject.GrpcClientBean",
        "net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor",
        "net.devh.boot.grpc.client.interceptor.GrpcGlobalClientInterceptor",
        "io.grpc.stub.annotations.GrpcGenerated",
    ).map { AnnotationName(it) }.toSet()

    private val GRPC_MODIFIERS = setOf(
        "io.smallrye.common.annotation.Blocking",
        "io.smallrye.common.annotation.NonBlocking",
        "io.smallrye.common.annotation.RunOnVirtualThread",
        "io.quarkus.grpc.RegisterInterceptor",
        "io.quarkus.grpc.RegisterClientInterceptor",
    ).map { AnnotationName(it) }.toSet()

    private data class Preset(
        val entryPoints: Set<AnnotationName>,
        val modifiers: Set<AnnotationName> = emptySet(),
    ) {
        val all: Set<AnnotationName> get() = entryPoints + modifiers
    }

    private val PRESET_MAP: Map<String, Preset> = mapOf(
        "spring" to Preset(SPRING + JPA + JAKARTA + VALIDATION + GRPC, SPRING_MODIFIERS),
        "quarkus" to Preset(QUARKUS + JAXRS + CDI + MICROPROFILE + JPA + JAKARTA + VALIDATION + JACKSON + GRPC, MICROPROFILE_MODIFIERS + GRPC_MODIFIERS),
        "grpc" to Preset(GRPC, GRPC_MODIFIERS),
        "jaxrs" to Preset(JAXRS),
        "cdi" to Preset(CDI),
        "microprofile" to Preset(MICROPROFILE, MICROPROFILE_MODIFIERS),
        "jpa" to Preset(JPA),
        "jackson" to Preset(JACKSON),
        "jakarta" to Preset(JAKARTA),
        "validation" to Preset(VALIDATION),
        "junit" to Preset(JUNIT),
    )

    private val PRESETS: Map<String, Set<AnnotationName>> =
        PRESET_MAP.mapValues { (_, preset) -> preset.all }

    private val ANNOTATION_TO_FRAMEWORK: Map<AnnotationName, String> by lazy {
        val result = mutableMapOf<AnnotationName, String>()
        val specificity = listOf(
            "jpa" to JPA, "jackson" to JACKSON, "jaxrs" to JAXRS, "cdi" to CDI,
            "microprofile" to MICROPROFILE, "microprofile" to MICROPROFILE_MODIFIERS,
            "grpc" to GRPC, "grpc" to GRPC_MODIFIERS,
            "jakarta" to JAKARTA, "validation" to VALIDATION, "junit" to JUNIT,
            "quarkus" to QUARKUS, "spring" to SPRING, "spring" to SPRING_MODIFIERS,
        )
        for ((framework, annotations) in specificity) {
            for (annotation in annotations) {
                result.putIfAbsent(annotation, framework)
            }
        }
        result
    }

    fun resolve(framework: String): Set<AnnotationName> =
        PRESETS[framework.lowercase()] ?: emptySet()

    fun resolveEntryPoints(framework: String): Set<AnnotationName> =
        PRESET_MAP[framework.lowercase()]?.entryPoints ?: emptySet()

    fun resolveModifiers(framework: String): Set<AnnotationName> =
        PRESET_MAP[framework.lowercase()]?.modifiers ?: emptySet()

    fun resolveAll(frameworks: List<String>): Set<AnnotationName> =
        frameworks.flatMap { resolve(it) }.toSet()

    fun resolveAllEntryPoints(frameworks: List<String>): Set<AnnotationName> =
        frameworks.flatMap { resolveEntryPoints(it) }.toSet()

    fun resolveAllModifiers(frameworks: List<String>): Set<AnnotationName> =
        frameworks.flatMap { resolveModifiers(it) }.toSet()

    fun availablePresets(): Set<String> = PRESETS.keys

    fun resolveAllEntryPointsExcept(excluded: List<String>): Set<AnnotationName> =
        presetsExcept(excluded).flatMap { it.entryPoints }.toSet()

    fun resolveAllModifiersExcept(excluded: List<String>): Set<AnnotationName> =
        presetsExcept(excluded).flatMap { it.modifiers }.toSet()

    private fun presetsExcept(excluded: List<String>): Collection<Preset> {
        if (excluded.any { it.equals("ALL", ignoreCase = true) }) return emptyList()
        val excludedLower = excluded.map { it.lowercase() }.toSet()
        return PRESET_MAP.filterKeys { it !in excludedLower }.values
    }

    fun frameworkOf(annotation: AnnotationName): String? =
        ANNOTATION_TO_FRAMEWORK[annotation]
}
