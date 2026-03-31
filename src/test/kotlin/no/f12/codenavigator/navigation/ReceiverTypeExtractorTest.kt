package no.f12.codenavigator.navigation

import no.f12.codenavigator.navigation.deadcode.ReceiverTypeExtractor
import org.objectweb.asm.Opcodes
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReceiverTypeExtractorTest {

    // [TEST] Non-Kt class with same static method signature does not return receiver types
    // [TEST] Static method with no parameters returns no receiver types
    // [TEST] Multiple static methods with different receiver types returns all
    // [TEST] Instance method (non-static) does not contribute receiver types
    // [TEST] Primitive first parameter is ignored

    @Test
    fun `Kt class with static method having Route as first parameter returns that receiver type`() {
        val dir = createTempDirectory("receiver-test").toFile()
        TestClassWriter.writeClassFile(dir, "com/example/RoutesKt", "Routes.kt") {
            val mv = visitMethod(
                Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC,
                "registerApprovalRoute",
                "(Lio/ktor/server/routing/Route;Ljava/lang/String;)V",
                null,
                null,
            )
            mv.visitCode()
            mv.visitInsn(Opcodes.RETURN)
            mv.visitMaxs(0, 2)
            mv.visitEnd()
        }

        val result = ReceiverTypeExtractor.scanAll(listOf(dir))

        val receiverTypes = result[ClassName("com.example.RoutesKt")]
        assertEquals(setOf(ClassName("io.ktor.server.routing.Route")), receiverTypes)

        dir.deleteRecursively()
    }

    @Test
    fun `non-Kt class with same static method signature does not return receiver types`() {
        val dir = createTempDirectory("receiver-test").toFile()
        TestClassWriter.writeClassFile(dir, "com/example/Routes", "Routes.kt") {
            val mv = visitMethod(
                Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC,
                "registerApprovalRoute",
                "(Lio/ktor/server/routing/Route;Ljava/lang/String;)V",
                null,
                null,
            )
            mv.visitCode()
            mv.visitInsn(Opcodes.RETURN)
            mv.visitMaxs(0, 2)
            mv.visitEnd()
        }

        val result = ReceiverTypeExtractor.scanAll(listOf(dir))

        assertTrue(result.isEmpty(), "Non-Kt class should not have receiver types extracted")

        dir.deleteRecursively()
    }

    @Test
    fun `static method with no parameters returns no receiver types`() {
        val dir = createTempDirectory("receiver-test").toFile()
        TestClassWriter.writeClassFile(dir, "com/example/UtilKt", "Util.kt") {
            val mv = visitMethod(
                Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC,
                "doSomething",
                "()V",
                null,
                null,
            )
            mv.visitCode()
            mv.visitInsn(Opcodes.RETURN)
            mv.visitMaxs(0, 0)
            mv.visitEnd()
        }

        val result = ReceiverTypeExtractor.scanAll(listOf(dir))

        assertTrue(result.isEmpty(), "Method with no params should not produce receiver types")

        dir.deleteRecursively()
    }

    @Test
    fun `multiple static methods with different receiver types returns all`() {
        val dir = createTempDirectory("receiver-test").toFile()
        TestClassWriter.writeClassFile(dir, "com/example/RoutesKt", "Routes.kt") {
            val mv1 = visitMethod(
                Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC,
                "registerRouteA",
                "(Lio/ktor/server/routing/Route;)V",
                null,
                null,
            )
            mv1.visitCode()
            mv1.visitInsn(Opcodes.RETURN)
            mv1.visitMaxs(0, 1)
            mv1.visitEnd()

            val mv2 = visitMethod(
                Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC,
                "configureApp",
                "(Lio/ktor/server/application/Application;)V",
                null,
                null,
            )
            mv2.visitCode()
            mv2.visitInsn(Opcodes.RETURN)
            mv2.visitMaxs(0, 1)
            mv2.visitEnd()
        }

        val result = ReceiverTypeExtractor.scanAll(listOf(dir))

        val receiverTypes = result[ClassName("com.example.RoutesKt")]
        assertEquals(
            setOf(
                ClassName("io.ktor.server.routing.Route"),
                ClassName("io.ktor.server.application.Application"),
            ),
            receiverTypes,
        )

        dir.deleteRecursively()
    }

    @Test
    fun `instance method does not contribute receiver types`() {
        val dir = createTempDirectory("receiver-test").toFile()
        TestClassWriter.writeClassFile(dir, "com/example/RoutesKt", "Routes.kt") {
            val mv = visitMethod(
                Opcodes.ACC_PUBLIC,
                "registerRoute",
                "(Lio/ktor/server/routing/Route;)V",
                null,
                null,
            )
            mv.visitCode()
            mv.visitInsn(Opcodes.RETURN)
            mv.visitMaxs(0, 2)
            mv.visitEnd()
        }

        val result = ReceiverTypeExtractor.scanAll(listOf(dir))

        assertTrue(result.isEmpty(), "Instance methods should not be scanned for receiver types")

        dir.deleteRecursively()
    }

    @Test
    fun `primitive first parameter is ignored`() {
        val dir = createTempDirectory("receiver-test").toFile()
        TestClassWriter.writeClassFile(dir, "com/example/UtilKt", "Util.kt") {
            val mv = visitMethod(
                Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC,
                "doSomething",
                "(I)V",
                null,
                null,
            )
            mv.visitCode()
            mv.visitInsn(Opcodes.RETURN)
            mv.visitMaxs(0, 1)
            mv.visitEnd()
        }

        val result = ReceiverTypeExtractor.scanAll(listOf(dir))

        assertTrue(result.isEmpty(), "Primitive first parameter should not be treated as receiver type")

        dir.deleteRecursively()
    }
}
