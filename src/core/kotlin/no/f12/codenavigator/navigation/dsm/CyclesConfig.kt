package no.f12.codenavigator.navigation.dsm

import no.f12.codenavigator.ParamDef
import no.f12.codenavigator.TaskRegistry
import no.f12.codenavigator.config.OutputFormat
import no.f12.codenavigator.navigation.PackageName

data class CyclesConfig(
    val rootPackage: PackageName,
    val depth: Int,
    val format: OutputFormat,
) {
    companion object {
        fun parse(properties: Map<String, String?>): CyclesConfig = CyclesConfig(
            rootPackage = PackageName(properties["root-package"] ?: ""),
            depth = TaskRegistry.DSM_DEPTH.parse(properties["dsm-depth"]),
            format = ParamDef.parseFormat(properties),
        )
    }
}
