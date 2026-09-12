package com.example.variants.moveclass.original

// Same package as TaxCalculator — references it unqualified, with no import at all (none needed
// while both classes share a package). Moving TaxCalculator to a different package must add a new
// import here.
class TaxCalculatorUser {
    fun total(amount: Double): Double = amount + TaxCalculator().calculate(amount)
}
