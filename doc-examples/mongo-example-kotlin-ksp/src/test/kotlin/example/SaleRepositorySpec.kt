package example

import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@MicronautTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SaleRepositorySpec : AbstractMongoSpec() {

    @Inject
    lateinit var productRepository: ProductRepository

    @Inject
    lateinit var saleRepository: SaleRepository

    @Inject
    lateinit var manufacturerRepository: ManufacturerRepository

    @Test
    fun testReadWriteCustomType() {
        val apple = manufacturerRepository.save("Apple")
        val macBook = productRepository.save(Product("MacBook", apple))

        var sale = saleRepository.save(Sale(null, macBook, Quantity(1)))

        assertNotNull(
                sale.id
        )
        assertEquals(1, sale.quantity.amount)

        sale = saleRepository.findById(sale.id!!).orElse(sale)
        assertNotNull(sale)
        assertEquals(1, sale.quantity.amount)
    }
}
