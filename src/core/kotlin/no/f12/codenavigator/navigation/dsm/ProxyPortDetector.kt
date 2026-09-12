package no.f12.codenavigator.navigation.dsm

import no.f12.codenavigator.navigation.types.AnnotationName
import no.f12.codenavigator.navigation.types.ClassName

/**
 * Some frameworks generate a real adapter implementation for an interface at runtime — a dynamic
 * proxy — with no corresponding class ever compiled into the project's own bytecode. Spring Data
 * JPA/Mongo/Elasticsearch/Redis repositories and Quarkus Panache repositories are the common case:
 * `interface OwnerRepository extends JpaRepository<Owner, Integer>`, with no `OwnerRepositoryImpl`
 * class anywhere. Static bytecode analysis can never see that generated implementation, so without
 * this, such an interface has zero implementors and can never form an inversion boundary — the
 * interface itself just gets classified as an adapter (it extends a real framework type), collapsing
 * "port" and "the adapter that implements it" into one class instead of a real port/adapter pair.
 *
 * MicroProfile Rest Client (`@RegisterRestClient`) is the same pattern from the *client* side: a
 * project-owned interface describing an outbound HTTP call, implemented by a runtime-generated proxy.
 * There's no common supertype to detect it by (any plain interface can carry the annotation) — worse,
 * it's frequently also `@Path`-annotated, the exact annotation that otherwise marks a *server-side*
 * JAX-RS resource as a driving adapter (`AdapterReason.FRAMEWORK_ENTRY_POINT_ANNOTATION`). Detecting
 * `@RegisterRestClient` here, before that check runs, resolves the ambiguity: `RingGraphBuilder`
 * already excludes anything ProxyPortDetector claims from the adapter-finding set, so a Rest Client
 * interface becomes a real port with a synthetic proxy adapter instead of being misclassified as the
 * adapter itself, merely because it happens to share `@Path` with the server-side pattern.
 *
 * A detected interface gets a synthetic implementor injected into the ring graph, standing in for
 * the proxy cnav can never see directly — named `<Interface>$GeneratedProxy` and always labeled as
 * synthetic wherever it's printed, so it's never mistaken for a real compiled class.
 */
object ProxyPortDetector {

    const val PROXY_SUFFIX = "\$GeneratedProxy"

    private val PROXY_GENERATING_SUPERTYPES = setOf(
        "org.springframework.data.repository.Repository",
        "org.springframework.data.repository.CrudRepository",
        "org.springframework.data.repository.ListCrudRepository",
        "org.springframework.data.jpa.repository.JpaRepository",
        "org.springframework.data.repository.PagingAndSortingRepository",
        "org.springframework.data.repository.ListPagingAndSortingRepository",
        "org.springframework.data.repository.reactive.ReactiveCrudRepository",
        "org.springframework.data.repository.reactive.ReactiveSortingRepository",
        "org.springframework.data.mongodb.repository.MongoRepository",
        "org.springframework.data.mongodb.repository.ReactiveMongoRepository",
        "org.springframework.data.elasticsearch.repository.ElasticsearchRepository",
        "org.springframework.data.redis.repository.RedisRepository",
        "io.quarkus.hibernate.orm.panache.PanacheRepository",
        "io.quarkus.hibernate.orm.panache.PanacheRepositoryBase",
        "io.quarkus.hibernate.reactive.panache.PanacheRepository",
        "io.quarkus.hibernate.reactive.panache.PanacheRepositoryBase",
    ).map { ClassName(it) }.toSet()

    private val PROXY_GENERATING_ANNOTATIONS = setOf(
        "org.eclipse.microprofile.rest.client.inject.RegisterRestClient",
    )

    /** Returns port -> synthetic proxy implementor name. */
    fun detect(
        interfaces: Set<ClassName>,
        implementedBy: Map<ClassName, Set<ClassName>>,
        signatureTypes: Map<ClassName, Set<ClassName>>,
        classAnnotations: Map<ClassName, Set<AnnotationName>> = emptyMap(),
    ): Map<ClassName, ClassName> =
        interfaces
            .filter { implementedBy[it].orEmpty().isEmpty() }
            .filter { iface ->
                signatureTypes[iface].orEmpty().any { type -> type in PROXY_GENERATING_SUPERTYPES } ||
                    classAnnotations[iface].orEmpty().any { it.value in PROXY_GENERATING_ANNOTATIONS }
            }
            .associateWith { ClassName("${it.value}$PROXY_SUFFIX") }
}
