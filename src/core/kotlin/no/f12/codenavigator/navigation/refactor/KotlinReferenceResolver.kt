package no.f12.codenavigator.navigation.refactor

import org.jetbrains.kotlin.cli.common.config.addKotlinSourceRoot
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.cli.jvm.compiler.NoScopeRecordCliBindingTrace
import org.jetbrains.kotlin.cli.jvm.compiler.TopDownAnalyzerFacadeForJVM
import org.jetbrains.kotlin.cli.jvm.config.addJvmClasspathRoots
import org.jetbrains.kotlin.cli.jvm.config.configureJdkClasspathRoots
import org.jetbrains.kotlin.com.intellij.openapi.Disposable
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.descriptors.ConstructorDescriptor
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import java.io.File
import java.nio.file.Path

/**
 * K1 (classic frontend) semantic resolution over a whole source set + classpath. Lets a rewriter ask
 * "does this reference actually resolve to type X?" instead of matching names as text, eliminating the
 * heuristic false positives (a shadowing local, a same-named type in another package, a wildcard import).
 *
 * Built once per move via [tryBuild], which returns null on any failure so the caller can fall back to
 * heuristic matching — resolution needs a real compiled classpath, so it's a best-effort precision layer,
 * not a hard requirement. Owns a compiler [Disposable]; [close] it when done.
 *
 * (K1 is the legacy frontend, but it's the only resolution engine actually publishable/consumable outside
 * IntelliJ today — JetBrains explicitly does not publish the K2 Analysis API standalone artifacts for
 * external use, see KT-56203/KT-61419. It ships in the kotlin-compiler-embeddable we already depend on.)
 */
class KotlinReferenceResolver private constructor(
    private val disposable: Disposable,
    val ktFilesByPath: Map<String, KtFile>,
    private val bindingContext: BindingContext,
) : AutoCloseable {

    /** The FQN of the class/type this reference resolves to (unwrapping a constructor to its class), or null if unresolved. */
    fun resolvedClassFqn(ref: KtReferenceExpression): String? {
        val descriptor = bindingContext[BindingContext.REFERENCE_TARGET, ref] ?: return null
        val target = if (descriptor is ConstructorDescriptor) descriptor.constructedClass else descriptor
        return target.fqNameSafe.asString()
    }

    override fun close() = Disposer.dispose(disposable)

    companion object {
        fun tryBuild(sourceRoots: List<File>, classpath: List<Path>): KotlinReferenceResolver? {
            if (sourceRoots.isEmpty()) return null
            val disposable = Disposer.newDisposable("move-class-resolver")
            return try {
                val configuration = CompilerConfiguration().apply {
                    put(CommonConfigurationKeys.MODULE_NAME, "move-class-resolver")
                    put(CommonConfigurationKeys.MESSAGE_COLLECTOR_KEY, MessageCollector.NONE)
                    sourceRoots.filter { it.exists() }.forEach { addKotlinSourceRoot(it.absolutePath) }
                    addJvmClasspathRoots(classpath.map { it.toFile() }.filter { it.exists() })
                    configureJdkClasspathRoots()
                }
                val environment = KotlinCoreEnvironment.createForProduction(disposable, configuration, EnvironmentConfigFiles.JVM_CONFIG_FILES)
                val ktFiles = environment.getSourceFiles()
                if (ktFiles.isEmpty()) {
                    Disposer.dispose(disposable)
                    return null
                }
                val analysis = TopDownAnalyzerFacadeForJVM.analyzeFilesWithJavaIntegration(
                    environment.project, ktFiles, NoScopeRecordCliBindingTrace(environment.project),
                    configuration, environment::createPackagePartProvider,
                )
                val byPath = ktFiles.mapNotNull { file -> file.virtualFile?.path?.let { it to file } }.toMap()
                KotlinReferenceResolver(disposable, byPath, analysis.bindingContext)
            } catch (t: Throwable) {
                Disposer.dispose(disposable)
                null
            }
        }
    }
}
