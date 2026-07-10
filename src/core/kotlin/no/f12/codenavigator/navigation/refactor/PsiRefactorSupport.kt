package no.f12.codenavigator.navigation.refactor

import org.jetbrains.kotlin.cli.common.messages.MessageRenderer
import org.jetbrains.kotlin.cli.common.messages.PrintingMessageCollector
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.com.intellij.openapi.Disposable
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.psi.KtPsiFactory

/**
 * Creates an isolated Kotlin compiler frontend (own [Disposable], own [KotlinCoreEnvironment]).
 * Each rewriter needs its own instance — PSI trees aren't shared across rewriters, so a longer-lived
 * shared environment would only add cross-call leakage risk, not reuse benefit. Callers that process
 * many files per top-level call should prefer [withKotlinPsiFactory]; callers that are invoked once
 * per file from an external loop (e.g. [KotlinRenameMethodRewriter], [JavaRenameMethodRewriter]) call
 * this directly and cache + dispose the result themselves.
 */
internal fun createDisposableKotlinEnvironment(moduleName: String): Pair<Disposable, KotlinCoreEnvironment> {
    val disposable = Disposer.newDisposable(moduleName)
    val configuration = CompilerConfiguration().apply {
        put(
            CommonConfigurationKeys.MESSAGE_COLLECTOR_KEY,
            PrintingMessageCollector(System.err, MessageRenderer.PLAIN_RELATIVE_PATHS, false),
        )
        put(CommonConfigurationKeys.MODULE_NAME, moduleName)
    }
    val environment = KotlinCoreEnvironment.createForProduction(
        disposable, configuration, EnvironmentConfigFiles.JVM_CONFIG_FILES,
    )
    return disposable to environment
}

/**
 * Scoped variant for rewriters that process all their files within a single top-level call.
 * `inline` so callers can use early `return` from within [block] (non-local return).
 */
internal inline fun <T> withKotlinPsiFactory(moduleName: String, block: (KtPsiFactory) -> T): T {
    val (disposable, environment) = createDisposableKotlinEnvironment(moduleName)
    try {
        return block(KtPsiFactory(environment.project))
    } finally {
        Disposer.dispose(disposable)
    }
}

internal fun applyEdits(content: String, edits: List<TextEdit>): String {
    var result = content
    for (edit in edits.sortedByDescending { it.offset }) {
        result = result.substring(0, edit.offset) + edit.replacement + result.substring(edit.offset + edit.length)
    }
    return result
}
