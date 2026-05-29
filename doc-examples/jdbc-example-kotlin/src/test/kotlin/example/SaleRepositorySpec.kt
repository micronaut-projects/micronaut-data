package example

import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

@MicronautTest
class SaleRepositorySpec(
        private val productRepository: ProductRepository,
        private val saleRepository: SaleRepository,
        private val manufacturerRepository: ManufacturerRepository) {

    @Test
    fun testReadWriteCustomType() {
        val apple = manufacturerRepository.save("Apple")
        val macBook = Product(0,"MacBook", apple)
        productRepository.save(macBook)

        var sale = saleRepository.save(Sale(0, macBook, Quantity(1)))

        assertNotNull(
                sale.id
        )
        assertEquals(1, sale.quantity.amount)

        sale = saleRepository.findById(sale.id!!) ?: sale
        assertNotNull(sale)
        assertEquals(1, sale.quantity.amount)
    }
}
