package no.f12.codenavigator.gradle

import no.f12.codenavigator.navigation.refactor.SafeDeleteRewriter
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters

interface SafeDeleteWorkParameters : WorkParameters {
    val className: Property<String>
    val methodName: Property<String>
    val preview: Property<Boolean>
    val sourceRoots: ListProperty<String>
    val classDirectories: ListProperty<String>
    val resultFile: RegularFileProperty
}

abstract class SafeDeleteWorkAction : WorkAction<SafeDeleteWorkParameters> {

    override fun execute() {
        val params = parameters
        val sourceRootFiles = params.sourceRoots.get().map { java.io.File(it) }
        val classDirFiles = params.classDirectories.get().map { java.io.File(it) }

        val result = SafeDeleteRewriter.delete(
            sourceRoots = sourceRootFiles,
            classDirectories = classDirFiles,
            className = params.className.get(),
            methodName = params.methodName.get().ifEmpty { null },
            preview = params.preview.get(),
        )

        params.resultFile.get().asFile.writeText(result.toJson())
    }
}
