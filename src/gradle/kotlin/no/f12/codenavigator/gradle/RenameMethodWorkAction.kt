package no.f12.codenavigator.gradle

import no.f12.codenavigator.navigation.refactor.RenameMethodRewriter
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters

interface RenameMethodWorkParameters : WorkParameters {
    val className: Property<String>
    val methodName: Property<String>
    val newName: Property<String>
    val preview: Property<Boolean>
    val sourceRoots: ListProperty<String>
    val resultFile: RegularFileProperty
}

abstract class RenameMethodWorkAction : WorkAction<RenameMethodWorkParameters> {

    override fun execute() {
        val params = parameters
        val sourceRootFiles = params.sourceRoots.get().map { java.io.File(it) }

        val result = RenameMethodRewriter.rename(
            sourceRoots = sourceRootFiles,
            className = params.className.get(),
            methodName = params.methodName.get(),
            newName = params.newName.get(),
            preview = params.preview.get(),
        )

        params.resultFile.get().asFile.writeText(result.toJson())
    }
}
