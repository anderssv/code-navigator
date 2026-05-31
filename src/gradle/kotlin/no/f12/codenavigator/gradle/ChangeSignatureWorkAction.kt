package no.f12.codenavigator.gradle

import no.f12.codenavigator.navigation.refactor.ChangeSignatureRewriter
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters

interface ChangeSignatureWorkParameters : WorkParameters {
    val className: Property<String>
    val methodName: Property<String>
    val params: Property<String>
    val defaults: MapProperty<String, String>
    val preview: Property<Boolean>
    val sourceRoots: ListProperty<String>
    val classDirectories: ListProperty<String>
    val resultFile: RegularFileProperty
}

abstract class ChangeSignatureWorkAction : WorkAction<ChangeSignatureWorkParameters> {

    override fun execute() {
        val p = parameters
        val sourceRootFiles = p.sourceRoots.get().map { java.io.File(it) }
        val classDirFiles = p.classDirectories.get().map { java.io.File(it) }

        val result = ChangeSignatureRewriter.change(
            sourceRoots = sourceRootFiles,
            classDirectories = classDirFiles,
            className = p.className.get(),
            methodName = p.methodName.get(),
            newParams = p.params.get(),
            defaults = p.defaults.get(),
            preview = p.preview.get(),
        )

        p.resultFile.get().asFile.writeText(result.toJson())
    }
}
