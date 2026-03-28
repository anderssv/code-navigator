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
        "org.springframework.cache.annotation.Cacheable",
        "org.springframework.cache.annotation.CacheEvict",
        "org.springframework.cache.annotation.CachePut",
        "org.springframework.transaction.annotation.Transactional",
        "org.springframework.scheduling.annotation.Async",
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

    private val PRESETS: Map<String, Set<AnnotationName>> = mapOf(
        "spring" to SPRING + JPA + JAKARTA + VALIDATION,
        "jpa" to JPA,
        "jackson" to JACKSON,
        "jakarta" to JAKARTA,
        "validation" to VALIDATION,
        "junit" to JUNIT,
    )

    private val ANNOTATION_TO_FRAMEWORK: Map<AnnotationName, String> by lazy {
        val result = mutableMapOf<AnnotationName, String>()
        val specificity = listOf("jpa" to JPA, "jackson" to JACKSON, "jakarta" to JAKARTA, "validation" to VALIDATION, "junit" to JUNIT, "spring" to SPRING)
        for ((framework, annotations) in specificity) {
            for (annotation in annotations) {
                result.putIfAbsent(annotation, framework)
            }
        }
        result
    }

    fun resolve(framework: String): Set<AnnotationName> =
        PRESETS[framework.lowercase()] ?: emptySet()

    fun resolveAll(frameworks: List<String>): Set<AnnotationName> =
        frameworks.flatMap { resolve(it) }.toSet()

    fun availablePresets(): Set<String> = PRESETS.keys

    fun frameworkOf(annotation: AnnotationName): String? =
        ANNOTATION_TO_FRAMEWORK[annotation]
}
