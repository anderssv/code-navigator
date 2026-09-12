package com.example.variants.moveclass.original

// Pure top-level-function file (no classes) — moving it exercises the Kt-facade path in
// MoveFileRewriter, which must also handle unqualified same-package callers of its functions.
fun checkmarkSvg(): String = "<svg>check</svg>"
