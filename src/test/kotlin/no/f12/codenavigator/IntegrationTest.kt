package no.f12.codenavigator

import no.f12.codenavigator.navigation.CallGraphBuilder
import no.f12.codenavigator.navigation.CallerTreeFormatter
import no.f12.codenavigator.navigation.ClassName
import no.f12.codenavigator.navigation.MethodRef
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import java.io.File

class IntegrationTest {

    @Test
    fun `transitive callers work with real compiled bytecode`() {
        val classesDir = File("test-project/build/classes/kotlin/main")
        if (!classesDir.exists()) {
            buildTestProject()
        }

        val graph = CallGraphBuilder.build(listOf(classesDir)).data

        // Direct callers of buildNotificationMessage
        val directCallers = graph.callersOf(ClassName("com.example.services.UserService"), "buildNotificationMessage")
        val directNames = directCallers.map { it.methodName }.toSet()
        assertTrue("sendResetNotification" in directNames, "sendResetNotification should call buildNotificationMessage")
        assertTrue("sendDeactivationNotification" in directNames, "sendDeactivationNotification should call buildNotificationMessage")

        // Callers of sendResetNotification
        val resetNotifCallers = graph.callersOf(ClassName("com.example.services.UserService"), "sendResetNotification")
        val resetNotifNames = resetNotifCallers.map { it.methodName }.toSet()
        assertTrue("resetPassword" in resetNotifNames, "resetPassword should call sendResetNotification, got: $resetNotifNames")

        // Callers of sendDeactivationNotification
        val deactivNotifCallers = graph.callersOf(ClassName("com.example.services.UserService"), "sendDeactivationNotification")
        val deactivNotifNames = deactivNotifCallers.map { it.methodName }.toSet()
        assertTrue("deactivateUser" in deactivNotifNames, "deactivateUser should call sendDeactivationNotification, got: $deactivNotifNames")

        // Callers of resetPassword
        val resetCallers = graph.callersOf(ClassName("com.example.services.UserService"), "resetPassword")
        val resetCallerNames = resetCallers.map { "${it.className.value}.${it.methodName}" }.toSet()
        assertTrue(
            resetCallerNames.any { it.contains("handleReset") },
            "handleReset should call resetPassword, got: $resetCallerNames",
        )

        // Full tree should show all levels
        val methods = listOf(MethodRef(ClassName("com.example.services.UserService"), "buildNotificationMessage"))
        val result = CallerTreeFormatter.format(graph, methods, maxDepth = 5)
        println("RESULT:\n$result")
        assertTrue(result.contains("sendResetNotification"), "Tree should show sendResetNotification")
        assertTrue(result.contains("resetPassword"), "Tree should show resetPassword at depth 2")
        assertTrue(result.contains("handleReset"), "Tree should show handleReset at depth 3")
    }

    private fun buildTestProject() {
        val testProjectDir = File("test-project")
        val gradlew = File(testProjectDir.parentFile, "gradlew").absolutePath
        val process = ProcessBuilder(gradlew, "classes")
            .directory(testProjectDir)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()
        check(exitCode == 0) { "Failed to build test-project (exit $exitCode): $output" }
    }
}
