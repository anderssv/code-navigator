// Graph shapes: fan-out to three callers of Repo, and a three-step chain.
package com.example.variants.callpaths.shapes

class A(private val repo: Repo) { fun go(id: Long) = repo.delete(id) }
class B(private val repo: Repo) { fun go(id: Long) = repo.delete(id) }
class C(private val repo: Repo) { fun go(id: Long) = repo.delete(id) }

class FanOut(private val a: A, private val b: B, private val c: C) {
    fun go(id: Long) {
        a.go(id)
        b.go(id)
        c.go(id)
    }
}

class Step3(private val repo: Repo) { fun run(id: Long) = repo.delete(id) }
class Step2(private val next: Step3) { fun run(id: Long) = next.run(id) }
class Step1(private val next: Step2) { fun run(id: Long) = next.run(id) }
