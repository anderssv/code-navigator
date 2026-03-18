package no.f12.codenavigator

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import java.io.File

class IntegrationTest {

    @Test
    fun `transitive callers work with real compiled bytecode`() {
        val classesDir = File("test-project/build/classes/kotlin/main")
        if (!classesDir.exists()) {
            println("SKIP: test-project not compiled, run ./gradlew classes in test-project first")
            return
        }

        val graph = CallGraphBuilder.build(listOf(classesDir))

        // Direct callers of buildNotificationMessage
        val directCallers = graph.callersOf("com.example.services.UserService", "buildNotificationMessage")
        val directNames = directCallers.map { it.methodName }.toSet()
        assertTrue("sendResetNotification" in directNames, "sendResetNotification should call buildNotificationMessage")
        assertTrue("sendDeactivationNotification" in directNames, "sendDeactivationNotification should call buildNotificationMessage")

        // Callers of sendResetNotification
        val resetNotifCallers = graph.callersOf("com.example.services.UserService", "sendResetNotification")
        val resetNotifNames = resetNotifCallers.map { it.methodName }.toSet()
        assertTrue("resetPassword" in resetNotifNames, "resetPassword should call sendResetNotification, got: $resetNotifNames")

        // Callers of sendDeactivationNotification
        val deactivNotifCallers = graph.callersOf("com.example.services.UserService", "sendDeactivationNotification")
        val deactivNotifNames = deactivNotifCallers.map { it.methodName }.toSet()
        assertTrue("deactivateUser" in deactivNotifNames, "deactivateUser should call sendDeactivationNotification, got: $deactivNotifNames")

        // Callers of resetPassword
        val resetCallers = graph.callersOf("com.example.services.UserService", "resetPassword")
        val resetCallerNames = resetCallers.map { "${it.className}.${it.methodName}" }.toSet()
        assertTrue(
            resetCallerNames.any { it.contains("handleReset") },
            "handleReset should call resetPassword, got: $resetCallerNames",
        )

        // Full tree should show all levels
        val methods = listOf(MethodRef("com.example.services.UserService", "buildNotificationMessage"))
        val result = CallerTreeFormatter.format(graph, methods, maxDepth = 5)
        println("RESULT:\n$result")
        assertTrue(result.contains("sendResetNotification"), "Tree should show sendResetNotification")
        assertTrue(result.contains("resetPassword"), "Tree should show resetPassword at depth 2")
        assertTrue(result.contains("handleReset"), "Tree should show handleReset at depth 3")
    }
}
