package no.f12.codenavigator.navigation

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
        "jakarta.annotation.PostConstruct",
        "jakarta.annotation.PreDestroy",
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
    )

    private val JPA = setOf(
        "jakarta.persistence.Entity",
        "jakarta.persistence.MappedSuperclass",
        "jakarta.persistence.Embeddable",
        "jakarta.persistence.Converter",
        "jakarta.persistence.EntityListeners",
    )

    private val JACKSON = setOf(
        "com.fasterxml.jackson.annotation.JsonCreator",
        "com.fasterxml.jackson.annotation.JsonProperty",
        "com.fasterxml.jackson.databind.annotation.JsonDeserialize",
        "com.fasterxml.jackson.databind.annotation.JsonSerialize",
    )

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
    )

    private val PRESETS: Map<String, Set<String>> = mapOf(
        "spring" to SPRING + JPA,
        "jpa" to JPA,
        "jackson" to JACKSON,
        "junit" to JUNIT,
    )

    private val ANNOTATION_TO_FRAMEWORK: Map<String, String> by lazy {
        val result = mutableMapOf<String, String>()
        val specificity = listOf("jpa" to JPA, "jackson" to JACKSON, "junit" to JUNIT, "spring" to SPRING)
        for ((framework, annotations) in specificity) {
            for (annotation in annotations) {
                result.putIfAbsent(annotation, framework)
            }
        }
        result
    }

    fun resolve(framework: String): Set<String> =
        PRESETS[framework.lowercase()] ?: emptySet()

    fun resolveAll(frameworks: List<String>): Set<String> =
        frameworks.flatMap { resolve(it) }.toSet()

    fun availablePresets(): Set<String> = PRESETS.keys

    fun frameworkOf(annotationFqn: String): String? =
        ANNOTATION_TO_FRAMEWORK[annotationFqn]
}
