package no.f12.codenavigator

object ConfigHelpText {
    fun generate(tool: BuildTool = BuildTool.GRADLE): String = buildString {
        val t = { goal: String -> tool.taskName(goal) }
        val p = { name: String, value: String -> tool.param(name, value) }
        val pf = { name: String -> tool.paramFlag(name) }
        val propType = when (tool) {
            BuildTool.GRADLE -> "Gradle project properties (-P flags)"
            BuildTool.MAVEN -> "Maven system properties (-D flags)"
        }

        appendLine("=== code-navigator: Configuration Reference ===")
        appendLine()
        appendLine("All parameters are passed as $propType.")
        appendLine("Example: ${tool.usage("find-class", p("pattern", "Service"), p("format", "json"))}")
        if (tool == BuildTool.GRADLE) {
            appendLine()
            appendLine("--- Gradle Configuration Block (persistent defaults) ---")
            appendLine()
            appendLine("  codeNavigator {")
            appendLine("      rootPackage = \"com.example\"   // default: \"\" (all packages)")
            appendLine("  }")
            appendLine()
            appendLine("  Properties set in the config block are used as defaults.")
            appendLine("  -P flags always override the config block.")
        }
        appendLine()
        appendLine("--- Global Parameters (all tasks) ---")
        appendLine()
        appendLine("  ${p("format", "json")}             Output as machine-readable JSON")
        appendLine("  ${p("llm", "true")}                Output in compact, token-efficient LLM format")
        appendLine()
        appendLine("--- Navigation Tasks ---")
        appendLine()
        appendLine("  ${p("pattern", "<regex>")}         Class/symbol regex (${t("find-class")}, ${t("find-symbol")}, ${t("class-detail")}, ${t("find-interfaces")})")
        appendLine("  ${p("method", "<regex>")}          Method regex (${t("find-callers")}, ${t("find-callees")}, ${t("find-usages")})")
        appendLine("  ${p("maxdepth", "<N>")}            Max call tree depth (${t("find-callers")}, ${t("find-callees")})")
        appendLine("  ${p("projectonly", "true")}        Hide JDK/stdlib classes (${t("find-callers")}, ${t("find-callees")}, ${t("package-deps")})")
        appendLine("  ${p("reverse", "true")}            Show reverse dependencies (${t("package-deps")})")
        appendLine("  ${p("package", "<regex>")}         Filter packages by regex (${t("package-deps")})")
        appendLine("  ${p("includetest", "true")}        Include test source set (${t("find-interfaces")})")
        appendLine("  ${p("owner", "<class>")}           FQN of type — matches method call and field owners (${t("find-usages")})")
        appendLine("  ${p("type", "<class>")}            Find ALL references to a class: calls, fields, casts, signatures (${t("find-usages")})")
        appendLine()
        appendLine("--- DSM (Dependency Structure Matrix) ---")
        appendLine()
        appendLine("  ${p("root-package", "<pkg>")}      Only include packages under this prefix (${t("dsm")}, default: all)")
        appendLine("  ${p("dsm-depth", "<N>")}           Package grouping depth (${t("dsm")}, default: 2)")
        appendLine("  ${p("dsm-html", "<path>")}         Write interactive HTML matrix to file (${t("dsm")})")
        appendLine()
        appendLine("--- Git History Analysis ---")
        appendLine()
        appendLine("  ${p("after", "YYYY-MM-DD")}        Only consider commits after this date (default: 1 year ago)")
        appendLine("  ${pf("no-follow")}               Disable git rename tracking (renames tracked by default)")
        appendLine("  ${p("top", "<N>")}                 Max results (${t("hotspots")}, ${t("code-age")}, ${t("authors")}, ${t("churn")}, default: 50)")
        appendLine("  ${p("min-revs", "<N>")}            Min revisions to include (${t("hotspots")}, ${t("authors")}, default: 1)")
        appendLine("  ${p("min-shared-revs", "<N>")}     Min shared commits (${t("coupling")}, default: 5)")
        appendLine("  ${p("min-coupling", "<N>")}        Min coupling degree % (${t("coupling")}, default: 30)")
        appendLine("  ${p("max-changeset-size", "<N>")}  Skip commits touching more files (${t("coupling")}, default: 30)")
    }
}
