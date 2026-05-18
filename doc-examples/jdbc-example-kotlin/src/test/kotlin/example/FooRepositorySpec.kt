package example

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest

@MicronautTest
class FooRepositoryTest(
    private val fooRepository: FooRepository,
    private val barRepository: BarRepository,
) : ShouldSpec({

    should("update foo reference") {
        val parentFoo = fooRepository.save(Foo(name = "parent"))
        val childFoo = fooRepository.save(Foo(name = "child"))

        fooRepository.update(parentFoo.id!!, childFoo)

        fooRepository.findById(parentFoo.id).get().foo shouldBe childFoo
    }

    should("update bar reference") {
        val parentFoo = fooRepository.save(Foo(name = "parent"))
        val childBar = barRepository.save(Bar(title = "child"))

        fooRepository.update(parentFoo.id!!, childBar)

        fooRepository.findById(parentFoo.id).get().bar shouldBe childBar
    }

    should("update name") {
        val parentFoo = fooRepository.save(Foo(name = "parent"))

        val newName = "NEW"

        fooRepository.update(parentFoo.id!!, newName)

        fooRepository.findById(parentFoo.id).get().name shouldBe newName
    }
})
