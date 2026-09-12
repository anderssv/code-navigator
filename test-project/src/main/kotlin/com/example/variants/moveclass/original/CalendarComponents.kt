package com.example.variants.moveclass.original

// Same package as SvgIcons.kt — calls checkmarkSvg() unqualified, with no import at all (none
// needed while both files share a package). Moving SvgIcons.kt to a different package must add
// a new import here, not just rewrite an existing one.
fun renderCalendar(): String = checkmarkSvg()
