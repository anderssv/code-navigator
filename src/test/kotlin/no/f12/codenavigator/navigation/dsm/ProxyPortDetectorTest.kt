package no.f12.codenavigator.navigation.dsm

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
}
