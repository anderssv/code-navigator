package no.f12.codenavigator.gradle

import no.f12.codenavigator.navigation.refactor.RenameParamRewriter
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters

interface RenameParamWorkParameters : WorkParameters {
    val className: Property<String>
    val methodName: Property<String>
    val paramName: Property<String>
    val newName: Property<String>
    val preview: Property<Boolean>
    val sourceRoots: ListProperty<String>
    val resultFile: RegularFileProperty
}

abstract class RenameParamWorkAction : WorkAction<RenameParamWorkParameters> {

    override fun execute() {
        val params = parameters
        val sourceRootFiles = params.sourceRoots.get().map { java.io.File(it) }

        val result = RenameParamRewriter.rename(
            sourceRoots = sourceRootFiles,
            className = params.className.get(),
            methodName = params.methodName.get(),
            paramName = params.paramName.get(),
            newName = params.newName.get(),
            apply = !params.preview.get(),
        )

        params.resultFile.get().asFile.writeText(result.toJson())
    }
}
