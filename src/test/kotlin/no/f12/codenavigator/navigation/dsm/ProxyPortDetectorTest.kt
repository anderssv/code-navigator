package no.f12.codenavigator.navigation.dsm

import no.f12.codenavigator.navigation.types.AnnotationName
import no.f12.codenavigator.navigation.types.ClassName
import kotlin.test.Test
import kotlin.test.assertEquals

class ProxyPortDetectorTest {

    @Test
    fun `an interface extending a Spring Data supertype with no compiled implementor is a proxy port`() {
        val repository = ClassName("com.app.OwnerRepository")

        val proxyPorts = ProxyPortDetector.detect(
            interfaces = setOf(repository),
            implementedBy = emptyMap(),
            signatureTypes = mapOf(repository to setOf(ClassName("org.springframework.data.jpa.repository.JpaRepository"))),
        )

        assertEquals(setOf(repository), proxyPorts.keys)
        assertEquals(ClassName("com.app.OwnerRepository\$GeneratedProxy"), proxyPorts[repository])
    }

    @Test
    fun `an interface with a real compiled implementor is not a proxy port`() {
        val port = ClassName("com.app.PollsRepository")
        val impl = ClassName("com.app.PollsRepositoryImpl")

        val proxyPorts = ProxyPortDetector.detect(
            interfaces = setOf(port),
            implementedBy = mapOf(port to setOf(impl)),
            signatureTypes = mapOf(port to setOf(ClassName("org.springframework.data.jpa.repository.JpaRepository"))),
        )

        assertEquals(emptySet(), proxyPorts.keys)
    }

    @Test
    fun `an interface not extending a known proxy-generating supertype is not a proxy port`() {
        val port = ClassName("com.app.PollsRepository")

        val proxyPorts = ProxyPortDetector.detect(
            interfaces = setOf(port),
            implementedBy = emptyMap(),
            signatureTypes = mapOf(port to setOf(ClassName("com.app.Poll"))),
        )

        assertEquals(emptySet(), proxyPorts.keys)
    }

    @Test
    fun `a Quarkus Panache repository is also detected`() {
        val repository = ClassName("com.app.OwnerRepository")

        val proxyPorts = ProxyPortDetector.detect(
            interfaces = setOf(repository),
            implementedBy = emptyMap(),
            signatureTypes = mapOf(repository to setOf(ClassName("io.quarkus.hibernate.orm.panache.PanacheRepository"))),
        )

        assertEquals(setOf(repository), proxyPorts.keys)
    }

    @Test
    fun `a non-interface class is never a proxy port`() {
        val cls = ClassName("com.app.Owner")

        val proxyPorts = ProxyPortDetector.detect(
            interfaces = emptySet(),
            implementedBy = emptyMap(),
            signatureTypes = mapOf(cls to setOf(ClassName("org.springframework.data.jpa.repository.JpaRepository"))),
        )

        assertEquals(emptySet(), proxyPorts.keys)
    }

    @Test
    fun `a MicroProfile Rest Client interface with no compiled implementor is a proxy port`() {
        // A real, reproducible case: MicroProfile Rest Client (Quarkus/Helidon/Open Liberty) generates
        // a runtime proxy implementor for an @RegisterRestClient interface, exactly like Spring Data
        // does for a repository -- but there is no common supertype to detect it by (any plain interface
        // can carry this annotation), so it needs its own annotation-based check rather than the
        // supertype-based one above.
        val client = ClassName("com.app.HeroRestClient")

        val proxyPorts = ProxyPortDetector.detect(
            interfaces = setOf(client),
            implementedBy = emptyMap(),
            signatureTypes = emptyMap(),
            classAnnotations = mapOf(client to setOf(AnnotationName("org.eclipse.microprofile.rest.client.inject.RegisterRestClient"))),
        )

        assertEquals(setOf(client), proxyPorts.keys)
        assertEquals(ClassName("com.app.HeroRestClient\$GeneratedProxy"), proxyPorts[client])
    }

    @Test
    fun `a MicroProfile Rest Client interface with a real compiled implementor is not a proxy port`() {
        val client = ClassName("com.app.HeroRestClient")
        val impl = ClassName("com.app.HeroRestClientImpl")

        val proxyPorts = ProxyPortDetector.detect(
            interfaces = setOf(client),
            implementedBy = mapOf(client to setOf(impl)),
            signatureTypes = emptyMap(),
            classAnnotations = mapOf(client to setOf(AnnotationName("org.eclipse.microprofile.rest.client.inject.RegisterRestClient"))),
        )

        assertEquals(emptySet(), proxyPorts.keys)
    }
}
