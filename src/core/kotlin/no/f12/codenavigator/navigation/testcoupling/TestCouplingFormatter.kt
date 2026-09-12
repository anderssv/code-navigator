package no.f12.codenavigator.navigation.testcoupling

import no.f12.codenavigator.navigation.types.ClassName

object TestCouplingFormatter {

    fun formatText(result: TestCouplingResult): String {
        val actionableViolations = result.actionableViolations
        if (actionableViolations.isEmpty()) return "No TTTD violations found. All test classes use domain-oriented setup."

        val (testViolations, adapterViolations) = actionableViolations.partition { it.subjectKind == CouplingSubjectKind.TEST }

        val sections = mutableListOf<String>()

        if (testViolations.isNotEmpty()) {
            sections += testViolations
                .groupBy { it.testClass }
                .map { (testClass, violations) ->
                    val verdict = result.verdictFor(testClass)
                    val shortName = testClass.value.substringAfterLast('.')
                    val confidence = result.confidenceFor(testClass)
                    "$shortName  verdict=$verdict  port-calls=${violations.size}  confidence=${"%.2f".format(confidence)}"
                }
                .joinToString("\n")
        }

        if (adapterViolations.isNotEmpty()) {
            sections += "Driving adapters calling a port write method directly (no service tier in between):\n" +
                adapterViolations
                    .groupBy { it.testClass }
                    .map { (adapterClass, violations) ->
                        val shortName = adapterClass.value.substringAfterLast('.')
                        "  $shortName  write-calls=${violations.size}"
                    }
                    .joinToString("\n")
        }

        return sections.joinToString("\n\n")
    }

    fun formatDetailText(result: TestCouplingResult): String {
        val actionableViolations = result.actionableViolations
        if (actionableViolations.isEmpty()) return "No TTTD violations found. All test classes use domain-oriented setup."

        val (testViolations, adapterViolations) = actionableViolations.partition { it.subjectKind == CouplingSubjectKind.TEST }

        val sections = mutableListOf<String>()

        if (testViolations.isNotEmpty()) {
            sections += testViolations
                .groupBy { it.testClass }
                .map { (testClass, violations) ->
                    val verdict = result.verdictFor(testClass)
                    val confidence = result.confidenceFor(testClass)
                    val shortName = testClass.value.substringAfterLast('.')
                    val header = "$shortName  verdict=$verdict  confidence=${"%.2f".format(confidence)}"
                    val calls = violations.map { v ->
                        val shortPort = v.portInterface.value.substringAfterLast('.')
                        "  ${v.testMethod} → ${shortPort}.${v.portMethod.methodName} [PORT]"
                    }
                    (listOf(header) + calls).joinToString("\n")
                }
                .joinToString("\n\n")
        }

        if (adapterViolations.isNotEmpty()) {
            sections += adapterViolations
                .groupBy { it.testClass }
                .map { (adapterClass, violations) ->
                    val shortName = adapterClass.value.substringAfterLast('.')
                    val header = "$shortName  [ADAPTER — no service tier between it and the port write below]"
                    val calls = violations.map { v ->
                        val shortPort = v.portInterface.value.substringAfterLast('.')
                        "  ${v.testMethod} → ${shortPort}.${v.portMethod.methodName} [PORT WRITE]"
                    }
                    (listOf(header) + calls).joinToString("\n")
                }
                .joinToString("\n\n")
        }

        return sections.joinToString("\n\n")
    }

    fun formatLlm(result: TestCouplingResult): String {
        val actionableViolations = result.actionableViolations
        if (actionableViolations.isEmpty()) return "No TTTD violations found. All test classes use domain-oriented setup."

        return actionableViolations.joinToString("\n") { v ->
            val shortTestClass = v.testClass.value.substringAfterLast('.')
            val shortPort = v.portInterface.value.substringAfterLast('.')
            val tag = if (v.subjectKind == CouplingSubjectKind.ADAPTER) " [ADAPTER, no service tier between]" else ""
            "$shortTestClass.${v.testMethod} -> ${shortPort}.${v.portMethod.methodName}$tag"
        }
    }
}
