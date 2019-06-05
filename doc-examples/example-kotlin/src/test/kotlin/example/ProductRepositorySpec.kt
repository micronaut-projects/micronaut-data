package example

import io.micronaut.test.annotation.MicronautTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.*

import javax.inject.Inject

@MicronautTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class ProductRepositorySpec {

    @Inject
    lateinit var productRepository: ProductRepository

    @BeforeAll
    fun setupData() {
        val apple = productRepository.save(Manufacturer(null,"Apple"))
        productRepository.saveAll(listOf(
                Product(null,
                        "MacBook",
                        apple
                ),
                Product(null,
                        "iPhone",
                        apple
                )
        ))
    }

    @Test
    fun testJoinSpec() {
        val list = productRepository.list()
        assertTrue(
            list.all { it.manufacturer.name == "Apple" }
        )
    }
}
