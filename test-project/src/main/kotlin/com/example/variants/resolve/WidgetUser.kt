package com.example.variants.resolve

enum class Kind { Widget, Other }

class WidgetUser {
    // Type reference + constructor — these SHOULD be renamed with the class.
    val w: Widget = Widget()

    // Enum entry that happens to share the class's simple name — must NOT be renamed
    // (a heuristic word-boundary rename would wrongly rewrite this to the new name).
    val k: Kind = Kind.Widget
}
