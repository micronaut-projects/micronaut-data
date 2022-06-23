package example

import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.*
import java.util.concurrent.TimeUnit

import jakarta.inject.Inject
import kotlinx.coroutines.runBlocking

@MicronautTest(transactional = false)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProductRepositoryTest : PostgresHibernateReactiveProperties {

    @Inject
    lateinit var productRepository: ProductRepository
    @Inject
    lateinit var manufacturerRepository: ManufacturerRepository

    @BeforeAll
    fun setupData(): Unit = runBlocking {
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
    fun testJoinSpec() = runBlocking {
        val list = productRepository.list()
        assertTrue(
            list.all { it.manufacturer.name == "Apple" }
        )
    }

}
