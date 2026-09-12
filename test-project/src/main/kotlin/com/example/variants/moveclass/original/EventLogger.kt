package com.example.variants.moveclass.original

// Same package as Events.kt — calls describeEvent() unqualified, with no import at all (none
// needed while both files share a package). Moving Events.kt (a multi-class file) must add a new
// import here for the top-level function, not just rewrite consumer imports of Event/EventProcessor.
fun logEvent(event: Event): Unit = println(describeEvent(event))
