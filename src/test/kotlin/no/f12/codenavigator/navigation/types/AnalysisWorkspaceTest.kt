package no.f12.codenavigator.navigation.types

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class AnalysisWorkspaceTest {

    @Test
    fun `single module workspace exposes ordinary tagged directories`() {
        val main = File("build/classes/main")
        val test = File("build/classes/test")
        val workspace = AnalysisWorkspace(
            modules = listOf(
                ModuleNode(
                    id = ModuleId(":"),
                    role = ModuleRole.SOURCE,
                    classDirectories = listOf(
                        TaggedClassDirectory(main, ModuleId(":"), SourceSet.MAIN),
                        TaggedClassDirectory(test, ModuleId(":"), SourceSet.TEST),
                    ),
                    sourceDirectories = listOf(
                        TaggedSourceDirectory(File("src/main"), ModuleId(":"), SourceSet.MAIN),
                        TaggedSourceDirectory(File("src/test"), ModuleId(":"), SourceSet.TEST),
                    ),
                ),
            ),
        )

        assertEquals(listOf(main to SourceSet.MAIN, test to SourceSet.TEST), workspace.taggedClassDirectories())
        assertEquals(listOf(main), workspace.classDirectories(Scope.PROD))
        assertEquals(listOf(test), workspace.classDirectories(Scope.TEST))
        assertEquals(listOf(File("src/main")), workspace.sourceDirectories(Scope.PROD))
    }

    @Test
    fun `workspace retains hierarchy and dependency relationships independently`() {
        val root = ModuleNode(
            id = ModuleId(":"),
            role = ModuleRole.SOURCE,
            dependencies = setOf(ModuleId(":shared")),
        )
        val shared = ModuleNode(
            id = ModuleId(":shared"),
            role = ModuleRole.DEPENDENCY,
            parentId = ModuleId(":"),
        )
        val workspace = AnalysisWorkspace(listOf(root, shared))

        assertEquals(shared, workspace.module(ModuleId(":shared")))
        assertEquals(listOf(shared), workspace.childrenOf(ModuleId(":")))
        assertEquals(setOf(shared), workspace.dependenciesOf(ModuleId(":")))
    }

    @Test
    fun `module directory tags retain provenance after flattening`() {
        val serviceDir = File("service/classes")
        val sharedDir = File("shared/classes")
        val workspace = AnalysisWorkspace(
            modules = listOf(
                ModuleNode(
                    id = ModuleId(":service"),
                    role = ModuleRole.SOURCE,
                    dependencies = setOf(ModuleId(":shared")),
                    classDirectories = listOf(TaggedClassDirectory(serviceDir, ModuleId(":service"), SourceSet.MAIN)),
                ),
                ModuleNode(
                    id = ModuleId(":shared"),
                    role = ModuleRole.DEPENDENCY,
                    classDirectories = listOf(TaggedClassDirectory(sharedDir, ModuleId(":shared"), SourceSet.MAIN)),
                ),
            ),
        )

        assertEquals(
            listOf(
                serviceDir to ModuleSourceSet(":service", SourceSet.MAIN),
                sharedDir to ModuleSourceSet(":shared", SourceSet.MAIN),
            ),
            workspace.moduleTaggedClassDirectories(),
        )
    }
}
