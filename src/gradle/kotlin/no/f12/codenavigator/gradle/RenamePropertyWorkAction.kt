package no.f12.codenavigator.gradle

import no.f12.codenavigator.navigation.refactor.RenamePropertyRewriter
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters

interface RenamePropertyWorkParameters : WorkParameters {
    val className: Property<String>
    val propertyName: Property<String>
    val newName: Property<String>
    val preview: Property<Boolean>
    val sourceRoots: ListProperty<String>
    val resultFile: RegularFileProperty
}

abstract class RenamePropertyWorkAction : WorkAction<RenamePropertyWorkParameters> {

    override fun execute() {
        val params = parameters
        val sourceRootFiles = params.sourceRoots.get().map { java.io.File(it) }

        val result = RenamePropertyRewriter.rename(
            sourceRoots = sourceRootFiles,
            className = params.className.get(),
            propertyName = params.propertyName.get(),
            newName = params.newName.get(),
            preview = params.preview.get(),
        )

        params.resultFile.get().asFile.writeText(result.toJson())
    }
}
