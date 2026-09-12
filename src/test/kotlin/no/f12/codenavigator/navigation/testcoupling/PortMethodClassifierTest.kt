package no.f12.codenavigator.navigation.testcoupling

import no.f12.codenavigator.navigation.types.ClassName
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PortMethodClassifierTest {

    private val repo = ClassName("com.example.PollsRepository")

    @Test
    fun `a name starting with a mutation verb is a write`() {
        for (name in listOf("updatePoll", "addPoll", "deletePoll", "finalizePoll", "createOrder", "removeItem")) {
            assertTrue(PortMethodClassifier.isWrite(repo, name, emptySet(), emptySet()), "$name should be a write")
        }
    }

    @Test
    fun `a name not starting with a mutation verb is a read`() {
        for (name in listOf("getPoll", "findById", "listAll", "existsByName", "count")) {
            assertFalse(PortMethodClassifier.isWrite(repo, name, emptySet(), emptySet()), "$name should be a read")
        }
    }

    @Test
    fun `writeMethods override forces a name that would otherwise read as a read`() {
        assertTrue(PortMethodClassifier.isWrite(repo, "archive", setOf("PollsRepository.archive"), emptySet()))
    }

    @Test
    fun `readMethods override forces a name that would otherwise read as a write`() {
        assertFalse(PortMethodClassifier.isWrite(repo, "ensureDevice", emptySet(), setOf("PollsRepository.ensureDevice")))
    }

    @Test
    fun `overrides are keyed by the port's simple name, not its fully qualified name`() {
        assertTrue(PortMethodClassifier.isWrite(ClassName("com.example.polls.PollsRepository"), "archive", setOf("PollsRepository.archive"), emptySet()))
    }
}
