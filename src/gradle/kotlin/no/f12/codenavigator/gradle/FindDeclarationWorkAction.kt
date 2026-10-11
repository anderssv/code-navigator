package no.f12.codenavigator.gradle

import no.f12.codenavigator.navigation.declaration.DeclarationInfo
import no.f12.codenavigator.navigation.declaration.DeclarationScanner
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import java.io.File

interface FindDeclarationWorkParameters : WorkParameters {
    val sourceRoots: ListProperty<String>
    val resultFile: RegularFileProperty
}

/** Runs the PSI scan inside the isolated compiler classloader; the task filters and formats the result. */
abstract class FindDeclarationWorkAction : WorkAction<FindDeclarationWorkParameters> {
    override fun execute() {
        val declarations = DeclarationScanner.scan(parameters.sourceRoots.get().map { File(it) })
        parameters.resultFile.get().asFile.writeText(DeclarationInfo.listToJson(declarations))
    }
}
