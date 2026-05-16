package no.f12.codenavigator.navigation.context

import no.f12.codenavigator.navigation.types.ClassName
import no.f12.codenavigator.navigation.relations.callgraph.CallTreeNode
import no.f12.codenavigator.navigation.classinfo.ClassDetail
import no.f12.codenavigator.navigation.relations.implementors.ImplementorInfo

data class ContextResult(
    val classDetail: ClassDetail,
    val callers: List<CallTreeNode>,
    val callees: List<CallTreeNode>,
    val implementors: List<ImplementorInfo>,
    val implementedInterfaces: List<ClassName>,
)

object ContextBuilder {

    fun build(
        classDetail: ClassDetail,
        callers: List<CallTreeNode>,
        callees: List<CallTreeNode>,
        implementors: List<ImplementorInfo>,
        implementedInterfaces: List<ClassName>,
    ): ContextResult = ContextResult(
        classDetail = classDetail,
        callers = callers,
        callees = callees,
        implementors = implementors,
        implementedInterfaces = implementedInterfaces,
    )
}
