package example

import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.*
import java.util.concurrent.TimeUnit

import jakarta.inject.Inject

@MicronautTest(transactional = false)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class ProductRepositorySpec {

    @Inject
    lateinit var productRepository: ProductRepository
    @Inject
    lateinit var manufacturerRepository: ManufacturerRepository

    @BeforeAll
    fun setupData() {
        productRepository.deleteAll()
        manufacturerRepository.deleteAll()
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
            list.all { it.manufacturer.name == "Apple" }
        )
    }

    @Test
    @Throws(Exception::class)
    fun testAsync() {
        // tag::async[]
        val total = productRepository.findByNameContains("o")
                .thenCompose { product -> productRepository.countByManufacturerName(product.manufacturer.name) }
                .get(1000, TimeUnit.SECONDS)

        assertEquals(
                2,
                total
        )
        // end::async[]
    }

    @Test
    @Throws(Exception::class)
    fun testReactive() {
        // tag::reactive[]
        val total = productRepository.queryByNameContains("o")
                .flatMap { product ->
                    productRepository.countDistinctByManufacturerName(product.manufacturer.name)
                            .toMaybe()
                }
                .defaultIfEmpty(0L)
                .blockingGet()

        assertEquals(
                2,
                total
        )
        // end::reactive[]
    }

    @Test
    fun testFindCaseInsensitive() {
        val totalCaseInsensitive = productRepository.findByName("macbook", true, false).size
        val totalCaseSensitive = productRepository.findByName("macbook", false, false).size

        assertEquals(
                1,
                totalCaseInsensitive
        )
        assertEquals(
                0,
                totalCaseSensitive
        )
    }
}
