package com.example.variants.movefile.web

// Uses declarations of its own package without importing them (same package). Moving this file to
// another package must add imports for them.
fun serve(): String {
    if (dataSourceRoutes().isEmpty()) throw NotFoundException("none")
    return DEFAULT_ROUTE
}
