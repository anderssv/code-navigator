package no.f12.codenavigator.gradle

import no.f12.codenavigator.navigation.refactor.MoveClassRewriter
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import java.nio.file.Path

interface MoveClassWorkParameters : WorkParameters {
    val className: Property<String>
    val newPackage: Property<String>
    val preview: Property<Boolean>
    val sourceRoots: ListProperty<String>
    val classpathDirs: ListProperty<String>
    val resultFile: RegularFileProperty
}

abstract class MoveClassWorkAction : WorkAction<MoveClassWorkParameters> {

    override fun execute() {
        val params = parameters
        val sourceRootFiles = params.sourceRoots.get().map { java.io.File(it) }
        val classpath = params.classpathDirs.get().map { Path.of(it) }

        val result = MoveClassRewriter.move(
            sourceRoots = sourceRootFiles,
            className = params.className.get(),
            newPackage = params.newPackage.get(),
            classpath = classpath,
            preview = params.preview.get(),
        )

        params.resultFile.get().asFile.writeText(result.toJson())
    }
}
