package no.f12.codenavigator.registry

import no.f12.codenavigator.gradle.CodeNavigatorPlugin
import no.f12.codenavigator.gradle.CodeNavigatorTask
import org.gradle.api.tasks.options.Option
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Ensures that Gradle @Option declarations and Maven @Parameter declarations stay in sync
 * with TaskRegistry — the single source of truth for all task parameters.
 *
 * WHY THIS TEST EXISTS:
 * TaskRegistry defines which parameters each task accepts. Gradle tasks declare @Option annotations
 * and Maven mojos declare @Parameter annotations. Without this test, it's easy to add a param to
 * TaskRegistry but forget to add the corresponding @Option/@Parameter — or vice versa. This test
 * catches that drift at compile+test time rather than at runtime when a user tries to use the param.
 *
 * HOW IT WORKS:
 * - Gradle: Uses reflection to read @Option annotations from task classes (compiled and on classpath)
 * - Maven: Parses source files for @Parameter(property = "...") since Maven classes aren't on Gradle's
 *   test classpath (they depend on maven-plugin-api which is only in the Maven build)
 */
class TaskOptionSyncTest {

    /**
     * Verifies every non-deprecated param in each TaskDef has a matching @Option annotation
     * on the corresponding Gradle task class (or its CodeNavigatorTask superclass for format/llm).
     */
    @Test
    fun `Gradle task @Option declarations match TaskRegistry params`() {
        val sharedOptionNames = collectOptionNames(CodeNavigatorTask::class.java)
        val failures = mutableListOf<String>()

        for (taskDef in TaskRegistry.ALL_TASKS) {
            val taskClass = CodeNavigatorPlugin.TASK_CLASSES[taskDef.goal] ?: continue
            val taskOptionNames = collectOptionNames(taskClass) + sharedOptionNames

            val expectedParams = taskDef.params
                .map { it.name }
                .toSet()

            val missingOptions = expectedParams - taskOptionNames
            val extraOptions = (taskOptionNames - expectedParams)
                .filter { it != "format" && it != "llm" && it != "plan-file" } // base class provides these even if not in params

            if (missingOptions.isNotEmpty()) {
                failures.add("Task '${taskDef.goal}' (${taskClass.simpleName}): missing @Option for params: $missingOptions")
            }
            if (extraOptions.isNotEmpty()) {
                failures.add("Task '${taskDef.goal}' (${taskClass.simpleName}): has @Option not in TaskRegistry: $extraOptions")
            }
        }

        if (failures.isNotEmpty()) {
            fail("Gradle @Option / TaskRegistry sync failures:\n" + failures.joinToString("\n"))
        }
    }

    /**
     * Verifies every non-deprecated param in each TaskDef has a matching @Parameter(property = "...")
     * in the corresponding Maven mojo source file.
     *
     * Uses source parsing because Maven mojo classes aren't on the Gradle test classpath.
     */
    @Test
    fun `Maven mojo @Parameter declarations match TaskRegistry params`() {
        val mojoDir = File("src/maven/kotlin/no/f12/codenavigator/maven")
        if (!mojoDir.exists()) {
            // Skip if maven sources not present (e.g., in a partial checkout)
            return
        }

        val goalToMojoFile = mapGoalsToMojoFiles(mojoDir)
        val failures = mutableListOf<String>()

        for (taskDef in TaskRegistry.ALL_TASKS) {
            val mojoFile = goalToMojoFile[taskDef.goal] ?: continue
            val declaredProperties = extractMavenParameterProperties(mojoFile)

            val expectedParams = taskDef.params
                .map { it.name }
                .toSet()

            val missingParams = expectedParams - declaredProperties
            val extraParams = declaredProperties - expectedParams

            if (missingParams.isNotEmpty()) {
                failures.add("Mojo for '${taskDef.goal}' (${mojoFile.name}): missing @Parameter for params: $missingParams")
            }
            if (extraParams.isNotEmpty()) {
                failures.add("Mojo for '${taskDef.goal}' (${mojoFile.name}): has @Parameter not in TaskRegistry: $extraParams")
            }
        }

        if (failures.isNotEmpty()) {
            fail("Maven @Parameter / TaskRegistry sync failures:\n" + failures.joinToString("\n"))
        }
    }

    /**
     * Collects all @Option(option = "...") names declared on a class (not its superclass).
     */
    private fun collectOptionNames(clazz: Class<*>): Set<String> {
        val options = mutableSetOf<String>()
        // Check fields (Kotlin properties compile to fields with annotations)
        for (field in clazz.declaredFields) {
            field.getAnnotation(Option::class.java)?.let { options.add(it.option) }
        }
        // Check methods (Kotlin @get:Internal moves annotation to getter; @Option may be on setter)
        for (method in clazz.declaredMethods) {
            method.getAnnotation(Option::class.java)?.let { options.add(it.option) }
        }
        return options
    }

    /**
     * Maps task goals to their Maven mojo source files by looking for @Mojo(name = "goal")
     */
    private fun mapGoalsToMojoFiles(mojoDir: File): Map<String, File> {
        val mojoNamePattern = Regex("""@Mojo\(\s*name\s*=\s*"([^"]+)"""")
        val map = mutableMapOf<String, File>()
        for (file in mojoDir.listFiles()?.filter { it.extension == "kt" } ?: emptyList()) {
            val content = file.readText()
            mojoNamePattern.find(content)?.let { match ->
                map[match.groupValues[1]] = file
            }
        }
        return map
    }

    /**
     * Extracts all @Parameter(property = "...") values from a Maven mojo source file.
     * Excludes the injected MavenProject parameter (property = "\${project}").
     */
    private fun extractMavenParameterProperties(file: File): Set<String> {
        val paramPattern = Regex("""@Parameter\([^)]*property\s*=\s*"([^"]+)"[^)]*\)""")
        return file.readText().let { content ->
            paramPattern.findAll(content)
                .map { it.groupValues[1] }
                .filter { !it.startsWith("\${") } // Exclude ${project} etc.
                .toSet()
        }
    }
}
