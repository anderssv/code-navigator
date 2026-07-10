package no.f12.codenavigator.gradle

import no.f12.codenavigator.navigation.refactor.BatchMoveRequest
import no.f12.codenavigator.navigation.refactor.MoveClassResult
import no.f12.codenavigator.navigation.refactor.MoveClassRewriter
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import java.nio.file.Path

interface MoveBatchWorkParameters : WorkParameters {
    val froms: ListProperty<String>
    val tos: ListProperty<String>
    val preview: Property<Boolean>
    val sourceRoots: ListProperty<String>
    val classpathDirs: ListProperty<String>
    val resultFile: RegularFileProperty
}

/**
 * Runs a whole batch of class moves (e.g. every class in a `cnavMovePackage`) in one
 * [MoveClassRewriter.moveBatch] call inside one isolated classloader — one parse instead of one
 * per class. See [MoveClassRewriter.moveBatch] for why this matters (each parse loads the full
 * Kotlin compiler frontend, which dominates the cost of moving many classes at once).
 */
abstract class MoveBatchWorkAction : WorkAction<MoveBatchWorkParameters> {

    override fun execute() {
        val params = parameters
        val sourceRootFiles = params.sourceRoots.get().map { java.io.File(it) }
        val classpath = params.classpathDirs.get().map { Path.of(it) }
        val moves = params.froms.get().zip(params.tos.get()) { from, to -> BatchMoveRequest(from, to) }

        val results = MoveClassRewriter.moveBatch(
            sourceRoots = sourceRootFiles,
            moves = moves,
            classpath = classpath,
            preview = params.preview.get(),
        )

        params.resultFile.get().asFile.writeText(MoveClassResult.listToJson(results))
    }
}
