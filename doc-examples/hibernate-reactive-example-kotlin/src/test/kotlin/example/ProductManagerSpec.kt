package example

import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@MicronautTest(transactional = false)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class ProductManagerSpec : PostgresHibernateReactiveProperties {
    @Inject
    private lateinit var productManager: ProductManager

    @Inject
    private lateinit var productRepository: ProductRepository

    @Inject
    private lateinit var manufacturerRepository: ManufacturerRepository

    @BeforeAll
    fun setupTest() : Unit = runBlocking {
        productRepository.deleteAll()
        manufacturerRepository.deleteAll()
    }

    @Test
    fun testProductManager() : Unit = runBlocking {
        val apple = manufacturerRepository.save("Apple")
        productManager.save("VR", apple).block()
        val (_, name) = productManager.find("VR").block()
        Assertions.assertEquals("VR", name)
    }
}
