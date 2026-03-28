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
        "GetMapping",
        "PostMapping",
        "PutMapping",
        "DeleteMapping",
        "PatchMapping",
        "RequestMapping",
        "ModelAttribute",
        "InitBinder",
        "ResponseBody",
        "RequestBody",
        "PathVariable",
        "RequestParam",
        "Autowired",
        "Value",
        "Qualifier",
        "Primary",
        "Lazy",
        "Scope",
        "Profile",
        "Conditional",
        "Import",
        "ImportResource",
        "PropertySource",
        "EnableCaching",
        "Cacheable",
        "CacheEvict",
        "CachePut",
        "Transactional",
        "Async",
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

    private val ANNOTATION_TO_FRAMEWORK: Map<String, String> by lazy {
        val result = mutableMapOf<String, String>()
        val specificity = listOf("jpa" to JPA, "jackson" to JACKSON, "spring" to SPRING)
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

    fun frameworkOf(annotationName: String): String? =
        ANNOTATION_TO_FRAMEWORK[annotationName]
}
