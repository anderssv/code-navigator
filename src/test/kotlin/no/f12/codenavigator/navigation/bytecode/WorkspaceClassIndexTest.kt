package no.f12.codenavigator.navigation.bytecode

import no.f12.codenavigator.navigation.TestClassWriter
import no.f12.codenavigator.navigation.types.AnalysisWorkspace
import no.f12.codenavigator.navigation.types.ClassName
import no.f12.codenavigator.navigation.types.ModuleId
import no.f12.codenavigator.navigation.types.ModuleNode
import no.f12.codenavigator.navigation.types.ModuleRole
import no.f12.codenavigator.navigation.types.SourceSet
import no.f12.codenavigator.navigation.types.TaggedClassDirectory
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

class WorkspaceClassIndexTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `retains every module when the same FQCN exists in more than one module`() {
        val serviceDir = tempDir.resolve("service").toFile().apply { mkdirs() }
        val sharedDir = tempDir.resolve("shared").toFile().apply { mkdirs() }
        TestClassWriter.writeClassFile(serviceDir, "com/example/Duplicate", "Duplicate.kt")
        TestClassWriter.writeClassFile(sharedDir, "com/example/Duplicate", "Duplicate.kt")
        val workspace = AnalysisWorkspace(
            modules = listOf(
                ModuleNode(ModuleId(":service"), ModuleRole.SOURCE, classDirectories = listOf(TaggedClassDirectory(serviceDir, ModuleId(":service"), SourceSet.MAIN))),
                ModuleNode(ModuleId(":shared"), ModuleRole.DEPENDENCY, classDirectories = listOf(TaggedClassDirectory(sharedDir, ModuleId(":shared"), SourceSet.MAIN))),
            ),
            moduleAware = true,
        )

        assertEquals(
            setOf(":service", ":shared"),
            workspace.modulesOfClass()[ClassName("com.example.Duplicate")],
        )
    }
}
