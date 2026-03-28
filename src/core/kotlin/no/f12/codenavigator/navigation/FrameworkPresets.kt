package no.f12.codenavigator.navigation

object FrameworkPresets {

    private val SPRING = setOf(
        "Controller",
        "RestController",
        "Service",
        "Component",
        "Repository",
        "Configuration",
        "Bean",
        "Scheduled",
        "EventListener",
        "PostConstruct",
        "PreDestroy",
        "ExceptionHandler",
        "ControllerAdvice",
        "Endpoint",
        "SpringBootApplication",
        "EnableAutoConfiguration",
        "ComponentScan",
    )

    private val JPA = setOf(
        "Entity",
        "MappedSuperclass",
        "Embeddable",
        "Converter",
        "EntityListeners",
    )

    private val JACKSON = setOf(
        "JsonCreator",
        "JsonProperty",
        "JsonDeserialize",
        "JsonSerialize",
    )

    private val PRESETS: Map<String, Set<String>> = mapOf(
        "spring" to SPRING + JPA,
        "jpa" to JPA,
        "jackson" to JACKSON,
    )

    fun resolve(framework: String): Set<String> =
        PRESETS[framework.lowercase()] ?: emptySet()

    fun resolveAll(frameworks: List<String>): Set<String> =
        frameworks.flatMap { resolve(it) }.toSet()

    fun availablePresets(): Set<String> = PRESETS.keys
}
