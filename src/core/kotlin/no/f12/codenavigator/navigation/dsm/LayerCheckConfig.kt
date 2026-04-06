package no.f12.codenavigator.navigation.dsm

import no.f12.codenavigator.config.OutputFormat
import no.f12.codenavigator.registry.ParamDef
import no.f12.codenavigator.registry.TaskRegistry

data class LayerCheckConfig(
    val configPath: String,
    val init: Boolean,
    val format: OutputFormat,
) {
    companion object {
        fun parse(properties: Map<String, String?>): LayerCheckConfig = LayerCheckConfig(
            configPath = TaskRegistry.LAYER_CONFIG.parseFrom(properties) ?: ".cnav-layers.json",
            init = TaskRegistry.INIT.parseFrom(properties),
            format = ParamDef.parseFormat(properties),
        )
    }
}
