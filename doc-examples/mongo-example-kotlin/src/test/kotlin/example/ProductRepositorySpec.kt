package example

import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.*
import java.util.concurrent.TimeUnit

import jakarta.inject.Inject

@MicronautTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class ProductRepositorySpec : AbstractMongoSpec() {

    @Inject
    lateinit var productRepository: ProductRepository

    @Inject
    lateinit var manufacturerRepository: ManufacturerRepository

    @BeforeAll
    fun setupData() {
        val apple = manufacturerRepository.save("Apple")
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
                list.all { it.manufacturer?.name == "Apple" }
        )
    }

    @Test
    @Throws(Exception::class)
    fun testAsync() {
        // tag::async[]
        val total = productRepository.findByNameRegex(".*o.*")
                .thenCompose { product -> productRepository.countByManufacturerName(product.manufacturer?.name) }
                .get(1000, TimeUnit.SECONDS)

        Assertions.assertEquals(
                2,
                total
        )
        // end::async[]
    }

    @Test
    @Throws(Exception::class)
    fun testReactive() {
        // tag::reactive[]
        val total = productRepository.queryByNameRegex(".*o.*")
                .flatMap { product ->
                    productRepository.countDistinctByManufacturerName(product.manufacturer?.name)
                            .toMaybe()
                }
                .defaultIfEmpty(0L)
                .blockingGet()

        Assertions.assertEquals(
                2,
                total
        )
        // end::reactive[]
    }
}
