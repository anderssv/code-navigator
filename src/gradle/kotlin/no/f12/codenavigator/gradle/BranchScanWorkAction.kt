package no.f12.codenavigator.gradle

import no.f12.codenavigator.navigation.branches.BranchCodec
import no.f12.codenavigator.navigation.branches.BranchScan
import no.f12.codenavigator.navigation.branches.BranchScanner
import org.gradle.api.Project
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import org.gradle.workers.WorkerExecutor
import java.io.File

interface BranchScanWorkParameters : WorkParameters {
    val files: ListProperty<String>
    val resultFile: RegularFileProperty
}

/** Runs the PSI branch scan inside the isolated compiler classloader; the result crosses back as text. */
abstract class BranchScanWorkAction : WorkAction<BranchScanWorkParameters> {
    override fun execute() {
        val functions = BranchScanner.scan(parameters.files.get().map { File(it) })
        parameters.resultFile.get().asFile.writeText(BranchCodec.encode(functions))
    }
}

/** A [BranchScan] over this project's source directories that parses in the PSI worker. */
fun Project.workerBranchScan(workerExecutor: WorkerExecutor, psiClasspath: FileCollection, temporaryDir: File): BranchScan =
    BranchScan(taggedSourceDirectories().map { it.first }) { files ->
        val result = File(temporaryDir, "branch-scan.txt")
        val queue = workerExecutor.classLoaderIsolation { classpath.from(psiClasspath) }
        queue.submit(BranchScanWorkAction::class.java) {
            this.files.set(files.map { it.absolutePath })
            resultFile.set(result)
        }
        queue.await()
        BranchCodec.decode(result.readText())
    }
