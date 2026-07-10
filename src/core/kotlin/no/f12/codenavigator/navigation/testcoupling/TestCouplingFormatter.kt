package no.f12.codenavigator.navigation.testcoupling

import no.f12.codenavigator.navigation.types.ClassName

object TestCouplingFormatter {

    fun formatText(result: TestCouplingResult): String {
        val actionableViolations = result.actionableViolations
        if (actionableViolations.isEmpty()) return "No TTTD violations found. All test classes use domain-oriented setup."

        val classSummaries = actionableViolations
            .groupBy { it.testClass }
            .map { (testClass, violations) ->
                val verdict = result.verdictFor(testClass)
                val shortName = testClass.value.substringAfterLast('.')
                val confidence = result.confidenceFor(testClass)
                "$shortName  verdict=$verdict  port-calls=${violations.size}  confidence=${"%.2f".format(confidence)}"
            }

        return classSummaries.joinToString("\n")
    }

    fun formatDetailText(result: TestCouplingResult): String {
        val actionableViolations = result.actionableViolations
        if (actionableViolations.isEmpty()) return "No TTTD violations found. All test classes use domain-oriented setup."

        return actionableViolations
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

    fun formatLlm(result: TestCouplingResult): String {
        val actionableViolations = result.actionableViolations
        if (actionableViolations.isEmpty()) return "No TTTD violations found. All test classes use domain-oriented setup."

        return actionableViolations.joinToString("\n") { v ->
            val shortTestClass = v.testClass.value.substringAfterLast('.')
            val shortPort = v.portInterface.value.substringAfterLast('.')
            "$shortTestClass.${v.testMethod} -> ${shortPort}.${v.portMethod.methodName}"
        }
    }
}
